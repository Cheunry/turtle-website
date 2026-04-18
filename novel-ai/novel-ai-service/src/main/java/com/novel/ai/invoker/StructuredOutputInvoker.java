package com.novel.ai.invoker;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;

/**
 * 结构化输出调用器——Novel AI "两级重试"架构的内层。
 * <p>
 * <b>职责切分</b>（参考 {@code com.novel.ai.advisor.RetryTransientAiAdvisor} 外层）：
 * <ul>
 *     <li>外层 Advisor：管模型通信——{@link TransientAiException}（限流 / 超时 / 网关 5xx）重试；</li>
 *     <li>本类 Invoker：管<b>结构化输出业务语义</b>——
 *         entity 解析失败（JSON 损坏、字段缺失、类型不匹配）时用"修复型 Prompt"引导模型自纠错。</li>
 * </ul>
 * 这样每次 {@code invoke} 最多产生
 * {@code advisor.retryMaxAttempts × structured.maxAttempts} 次实际调用，
 * 但真正打到模型的次数由 Advisor 去重，不会出现"业务重试×通信重试"的乘法爆炸
 * ——因为 Transient 在 Advisor 层就被消化或上抛，Invoker 这层看到的要么是成功，
 * 要么是彻底失败（耗尽 Transient 重试 / NonTransient 直接失败 / 解析失败）。
 * <p>
 * <b>不做的事</b>：
 * <ul>
 *     <li>不再埋 {@code ai.call.*} 的 span tag / 单次调用日志——这些由
 *         {@code StructuredOutputLogAdvisor} 统一处理，避免重复。</li>
 *     <li>不重试 {@link NonTransientAiException}（内容安全 / 鉴权 / 参数错误）——透传给上层。</li>
 *     <li>不重试 {@link TransientAiException}——Advisor 层已经重试过，到了这里说明已经耗尽，
 *         继续重试毫无意义。</li>
 * </ul>
 */
@Slf4j
@Component
public class StructuredOutputInvoker {

    private static final String STRICT_JSON_INSTRUCTION = """
请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
1) 不要输出 Markdown 代码块（如 ```json）。
2) 不要输出任何解释文字、前后缀、注释。
3) 所有字符串内引号必须正确转义。
""";

    private final NovelAiStructuredOutputProperties properties;

    public StructuredOutputInvoker(NovelAiStructuredOutputProperties properties) {
        this.properties = properties;
    }

    /**
     * 带"修复型 Prompt"的结构化输出调用（无额外 advisor）。
     * 等价于 {@link #invoke(ChatClient, String, String, BeanOutputConverter, String, Advisor...)}
     * 传空数组；为不破坏既有调用点保留。
     */
    public <T> T invoke(
            ChatClient chatClient,
            String systemPrompt,
            String userPrompt,
            BeanOutputConverter<T> outputConverter,
            String logContext) {
        return invoke(chatClient, systemPrompt, userPrompt, outputConverter, logContext, (Advisor[]) null);
    }

    /**
     * 带"修复型 Prompt"的结构化输出调用。
     *
     * @param chatClient      Spring AI ChatClient（已由 {@code AiConfig} 注入默认 Advisor 链）
     * @param systemPrompt    已拼好 format instructions 的 system prompt（调用方负责）
     * @param userPrompt      user prompt
     * @param outputConverter Bean 解析器
     * @param logContext      日志上下文标签（例如 "book-audit"），只用于日志与 Span，不影响业务
     * @param extraAdvisors   本次调用<b>局部追加</b>的 Advisor（例如 RAG 的
     *                        {@code RetrievalAugmentationAdvisor}）。只影响当前一次调用，
     *                        不会污染全局 defaultAdvisors 链。{@code null} 或空数组表示不追加。
     * @param <T>             目标对象类型
     * @return 解析成功的对象
     * @throws NonTransientAiException 若模型返回触发内容安全等不可重试错误
     * @throws TransientAiException    若 Advisor 层耗尽通信重试后仍失败
     * @throws RuntimeException        若达到 entity 解析重试上限仍失败，抛出最后一次异常
     */
    public <T> T invoke(
            ChatClient chatClient,
            String systemPrompt,
            String userPrompt,
            BeanOutputConverter<T> outputConverter,
            String logContext,
            Advisor... extraAdvisors) {

        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastError = null;
        boolean hasExtraAdvisors = extraAdvisors != null && extraAdvisors.length > 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String effectiveSystemPrompt = (attempt == 1)
                    ? systemPrompt
                    : buildRepairSystemPrompt(systemPrompt, lastError);

            try {
                ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                        .system(effectiveSystemPrompt)
                        .user(userPrompt);
                if (hasExtraAdvisors) {
                    spec = spec.advisors(extraAdvisors);
                }
                T result = spec.call().entity(outputConverter);

                if (attempt > 1) {
                    log.info("[{}] 结构化解析重试成功: attempt={}/{}", logContext, attempt, maxAttempts);
                    ActiveSpan.tag("ai.structured.outcome", "success_after_repair");
                    ActiveSpan.tag("ai.structured.attempts", String.valueOf(attempt));
                }
                return result;

            } catch (NonTransientAiException | TransientAiException e) {
                // 通信类异常（Transient 已被 Advisor 耗尽重试 / NonTransient 不可恢复）都透传给上层决策
                ActiveSpan.tag("ai.structured.outcome", e instanceof TransientAiException
                        ? "transient_exhausted" : "non_transient");
                log.warn("[{}] 结构化输出遇到 AI 通信异常，透传不在 Invoker 重试: {}: {}",
                        logContext, e.getClass().getSimpleName(), e.getMessage());
                throw e;

            } catch (RuntimeException e) {
                // 走到这里通常是：JSON 损坏 / 字段缺失 / 类型不匹配——业务语义问题，值得用修复型 Prompt 再试一次
                lastError = e;
                if (attempt < maxAttempts) {
                    log.warn("[{}] 结构化解析失败，准备用修复型 Prompt 重试: attempt={}/{}, error={}",
                            logContext, attempt, maxAttempts, e.getMessage());
                } else {
                    log.error("[{}] 结构化解析失败，已达业务重试上限: attempts={}, error={}",
                            logContext, maxAttempts, e.getMessage());
                }
            }
        }

        ActiveSpan.tag("ai.structured.outcome", "parse_failure");
        throw lastError != null
                ? lastError
                : new IllegalStateException("structured output invocation failed without captured error");
    }

    /**
     * 构造"修复型 Prompt"：在原 system prompt 后追加严格 JSON 指令 + 上次错误提示。
     */
    private String buildRepairSystemPrompt(String systemPrompt, RuntimeException lastError) {
        if (!properties.isRetryUseRepairPrompt()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt).append("\n\n");
        sb.append(STRICT_JSON_INSTRUCTION).append('\n');
        sb.append("上次输出解析失败，请仅返回合法 JSON，并严格贴合系统 Prompt 中的字段结构。");
        if (properties.isIncludeLastErrorInRetryPrompt() && lastError != null && lastError.getMessage() != null) {
            sb.append("\n上次失败原因：").append(sanitizeErrorMessage(lastError.getMessage()));
        }
        return sb.toString();
    }

    /**
     * 清理错误消息：去换行、截断到上限，避免把半个 stacktrace 塞进 Prompt。
     */
    private String sanitizeErrorMessage(String message) {
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        int max = Math.max(20, properties.getErrorMessageMaxLength());
        return oneLine.length() > max ? oneLine.substring(0, max) + "..." : oneLine;
    }
}
