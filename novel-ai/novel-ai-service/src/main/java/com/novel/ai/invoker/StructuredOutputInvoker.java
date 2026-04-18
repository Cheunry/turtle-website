package com.novel.ai.invoker;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;

/**
 * 结构化输出调用器：在 {@link ChatClient} 之上包一层"重试 + 修复型 Prompt"。
 * <p>
 * 设计目标：
 * <ul>
 *     <li>把"调用 LLM + 按 Schema 解析 + 失败重试"这一套样板从业务代码里抽出来，业务只关心业务。</li>
 *     <li>首次失败时在 system prompt 后追加"严格 JSON 指令 + 上次错误"，让模型自我纠错，显著提升成功率。</li>
 *     <li>对 {@link NonTransientAiException}（内容安全失败、模型明确拒绝）不做重试，直接抛出，避免无谓开销和成本浪费。</li>
 * </ul>
 * <p>
 * 不做的事：
 * <ul>
 *     <li>不捕获业务异常——成功就返回对象，失败就抛原始异常，由调用方按场景决定是"待审核 / 兜底 / 返回错误"。</li>
 *     <li>不打 Micrometer 指标——目前 novel-ai 没接 actuator，暂用 SkyWalking Span Tag 承载，后续统一接入。</li>
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
     * 带重试与修复型 Prompt 的结构化输出调用。
     *
     * @param chatClient      Spring AI ChatClient
     * @param systemPrompt    已拼好 format instructions 的 system prompt（调用方负责）
     * @param userPrompt      user prompt
     * @param outputConverter Bean 解析器
     * @param logContext      日志上下文标签（例如 "book-audit"），只用于日志与 Span，不影响业务
     * @param <T>             目标对象类型
     * @return 解析成功的对象
     * @throws NonTransientAiException 若模型返回内容触发内容安全等不可重试错误
     * @throws RuntimeException        若达到最大尝试次数仍解析失败，抛出最后一次异常
     */
    public <T> T invoke(
            ChatClient chatClient,
            String systemPrompt,
            String userPrompt,
            BeanOutputConverter<T> outputConverter,
            String logContext) {

        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String effectiveSystemPrompt = (attempt == 1)
                    ? systemPrompt
                    : buildRepairSystemPrompt(systemPrompt, lastError);

            long start = System.currentTimeMillis();
            try {
                T result = chatClient.prompt()
                        .system(effectiveSystemPrompt)
                        .user(userPrompt)
                        .call()
                        .entity(outputConverter);

                long cost = System.currentTimeMillis() - start;
                ActiveSpan.tag("ai.structured.attempt", String.valueOf(attempt));
                ActiveSpan.tag("ai.structured.duration.ms", String.valueOf(cost));
                ActiveSpan.tag("ai.structured.status", "success");
                if (attempt > 1) {
                    log.info("[{}] LLM 结构化输出重试成功: attempt={}/{}, cost={}ms", logContext, attempt, maxAttempts, cost);
                } else {
                    log.info("[{}] LLM 结构化输出成功: cost={}ms", logContext, cost);
                }
                return result;

            } catch (NonTransientAiException e) {
                // 内容安全 / 模型明确拒绝 -> 透传给上层决策，不再浪费次数重试
                ActiveSpan.tag("ai.structured.status", "non_transient_error");
                log.warn("[{}] 结构化输出遇到 NonTransientAiException，不再重试: {}", logContext, e.getMessage());
                throw e;

            } catch (RuntimeException e) {
                lastError = e;
                long cost = System.currentTimeMillis() - start;
                ActiveSpan.tag("ai.structured.attempt." + attempt + ".status", "failure");
                ActiveSpan.tag("ai.structured.attempt." + attempt + ".duration.ms", String.valueOf(cost));

                if (attempt < maxAttempts) {
                    log.warn("[{}] 结构化解析失败，准备重试: attempt={}/{}, cost={}ms, error={}",
                            logContext, attempt, maxAttempts, cost, e.getMessage());
                } else {
                    log.error("[{}] 结构化解析失败，已达最大重试次数: attempts={}, cost={}ms, error={}",
                            logContext, maxAttempts, cost, e.getMessage());
                }
            }
        }

        // 走到这里说明全部失败，抛最后一次异常给上层兜底
        ActiveSpan.tag("ai.structured.status", "failure");
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
