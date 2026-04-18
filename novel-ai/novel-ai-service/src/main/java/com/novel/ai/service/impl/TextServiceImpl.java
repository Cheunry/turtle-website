package com.novel.ai.service.impl;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.AuditPipeline;
import com.novel.ai.dto.req.AuditRuleReqDto;
import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.ai.dto.resp.AuditRuleRespDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.ai.invoker.StructuredOutputInvoker;
import com.novel.ai.model.AuditRuleAiOutput;
import com.novel.ai.model.TextPolishAiOutput;
import com.novel.ai.prompt.NovelAiPromptKey;
import com.novel.ai.prompt.NovelAiPromptLoader;
import com.novel.ai.service.TextService;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 文本相关 AI 能力入口。书籍审核、章节审核已经下沉到
 * {@code com.novel.ai.agent} 下的 Agent 流水线，本类仅做：
 * <ol>
 *     <li>组装审核请求上下文并交给对应 {@link AuditPipeline} 执行；</li>
 *     <li>承载流程较轻的能力：润色、封面提示词生成、审核经验规则抽取。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextServiceImpl implements TextService {

    private final ChatClient chatClient;
    private final NovelAiPromptLoader promptLoader;
    private final StructuredOutputInvoker structuredOutputInvoker;

    /** 书籍审核流水线，由 {@link com.novel.ai.agent.book.BookAuditPipelineFactory} 装配。 */
    private final AuditPipeline<BookAuditContext> bookAuditPipeline;

    /** 章节审核流水线，由 {@link com.novel.ai.agent.chapter.ChapterAuditPipelineFactory} 装配。 */
    private final AuditPipeline<ChapterAuditContext> chapterAuditPipeline;

    /** 无状态，复用即可，避免每次调用都重新构造 JsonSchema。 */
    private final BeanOutputConverter<TextPolishAiOutput> textPolishConverter =
            new BeanOutputConverter<>(TextPolishAiOutput.class);
    private final BeanOutputConverter<AuditRuleAiOutput> auditRuleConverter =
            new BeanOutputConverter<>(AuditRuleAiOutput.class);

    private static final int MIN_BOOK_DESC_LENGTH = 30;
    private static final int MAX_POLISH_TEXT_LENGTH = 10000;
    private static final int MIN_POLISH_TEXT_LENGTH = 10;

    @Override
    @Trace(operationName = "AI审核书籍")
    public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto reqDto) {
        BookAuditContext ctx = new BookAuditContext(reqDto);
        bookAuditPipeline.execute(ctx);
        return RestResp.ok(ctx.getResult());
    }

    @Override
    @Trace(operationName = "AI审核章节")
    public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto reqDto) {
        ChapterAuditContext ctx = new ChapterAuditContext(reqDto);
        chapterAuditPipeline.execute(ctx);
        return RestResp.ok(ctx.getResult());
    }

    @Override
    @Trace(operationName = "AI润色文本")
    public RestResp<TextPolishRespDto> polishText(TextPolishReqDto reqDto) {
        if (reqDto == null || reqDto.getSelectedText() == null) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "待润色文本不能为空");
        }

        String selectedText = reqDto.getSelectedText().trim();
        if (selectedText.isEmpty()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "待润色文本不能为空");
        }
        if (selectedText.length() < MIN_POLISH_TEXT_LENGTH) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR,
                    String.format("待润色文本长度不能少于%d个字符", MIN_POLISH_TEXT_LENGTH));
        }
        if (selectedText.length() > MAX_POLISH_TEXT_LENGTH) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR,
                    String.format("待润色文本长度不能超过%d个字符", MAX_POLISH_TEXT_LENGTH));
        }

        ActiveSpan.tag("ai.model", "text_model");
        ActiveSpan.tag("ai.operation", "polish_text");
        ActiveSpan.tag("text.length", String.valueOf(selectedText.length()));
        if (reqDto.getStyle() != null) {
            ActiveSpan.tag("polish.style", reqDto.getStyle());
        }

        long startTime = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.renderSystem(NovelAiPromptKey.TEXT_POLISH)
                    + "\n\n" + textPolishConverter.getFormat();
            Map<String, Object> userVars = new HashMap<>();
            userVars.put("selectedText", selectedText);
            userVars.put("style", reqDto.getStyle() != null ? reqDto.getStyle() : "通俗易懂");
            userVars.put("requirement", reqDto.getRequirement() != null
                    ? reqDto.getRequirement() : "保持原意，提升文学性");
            String userPrompt = promptLoader.renderUser(NovelAiPromptKey.TEXT_POLISH, userVars);
            ActiveSpan.tag("prompt.length", String.valueOf(systemPrompt.length() + userPrompt.length()));

            TextPolishAiOutput aiOutput = structuredOutputInvoker.invoke(
                    chatClient, systemPrompt, userPrompt, textPolishConverter, "text-polish");

            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "success");

            log.info("AI润色响应，耗时: {}ms, aiOutput: {}", duration, aiOutput);
            return RestResp.ok(buildTextPolishResp(aiOutput, selectedText));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "error");
            ActiveSpan.tag("ai.error.type", e.getClass().getSimpleName());
            ActiveSpan.error(e);
            log.error("AI润色异常，耗时: {}ms", duration, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI润色服务暂时不可用，请稍后再试");
        }
    }

    @Override
    @Trace(operationName = "AI生成封面提示词")
    public RestResp<String> getBookCoverPrompt(BookCoverReqDto reqDto) {
        ActiveSpan.tag("ai.model", "text_model");
        ActiveSpan.tag("ai.operation", "generate_cover_prompt");
        if (reqDto != null && reqDto.getId() != null) {
            ActiveSpan.tag("bookId", String.valueOf(reqDto.getId()));
        }

        long startTime = System.currentTimeMillis();
        try {
            if (reqDto == null || reqDto.getBookName() == null) {
                ActiveSpan.tag("ai.status", "validation_failed");
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
            }
            if (reqDto.getBookDesc() == null || reqDto.getBookDesc().trim().length() < MIN_BOOK_DESC_LENGTH) {
                ActiveSpan.tag("ai.status", "validation_failed");
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR,
                        "小说简介过短（需大于30字），无法生成精准封面");
            }

            String systemPrompt = promptLoader.renderSystem(NovelAiPromptKey.COVER_PROMPT);
            Map<String, Object> userVars = new HashMap<>();
            userVars.put("bookName", safe(reqDto.getBookName()));
            userVars.put("categoryName", reqDto.getCategoryName() != null ? reqDto.getCategoryName() : "通俗小说");
            String bookDesc = reqDto.getBookDesc();
            if (bookDesc.length() > 500) {
                bookDesc = bookDesc.substring(0, 500);
            }
            userVars.put("bookDesc", bookDesc);
            String userPrompt = promptLoader.renderUser(NovelAiPromptKey.COVER_PROMPT, userVars);
            ActiveSpan.tag("prompt.length", String.valueOf(systemPrompt.length() + userPrompt.length()));

            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "success");
            log.info("生成封面提示词响应，小说ID: {}, 耗时: {}ms, 响应: {}", reqDto.getId(), duration, aiResponse);

            String finalPrompt = aiResponse + "，高品质插画，书籍装帧风格，黄金比例构图，极致细节，最高解析度，(无文字，无水印：1.5)";
            return RestResp.ok(finalPrompt);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "error");
            ActiveSpan.tag("ai.error.type", e.getClass().getSimpleName());
            ActiveSpan.error(e);
            log.error("生成封面提示词异常，小说ID: {}, 耗时: {}ms",
                    reqDto != null ? reqDto.getId() : null, duration, e);
            return RestResp.fail(ErrorCodeEnum.AI_COVER_TEXT_SERVICE_ERROR, "生成封面提示词服务暂时不可用");
        }
    }

    @Override
    @Trace(operationName = "AI提取审核经验规则")
    public RestResp<AuditRuleRespDto> extractAuditRule(AuditRuleReqDto reqDto) {
        if (reqDto == null || reqDto.getContentText() == null || reqDto.getContentText().trim().isEmpty()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "待提取文本不能为空");
        }

        long startTime = System.currentTimeMillis();
        try {
            String content = reqDto.getContentText();
            if (content.length() > 2000) {
                content = content.substring(0, 2000);
            }

            String statusStr = reqDto.getAuditStatus() != null && reqDto.getAuditStatus() == 1 ? "通过" : "不通过";
            String reason = reqDto.getAuditReason() != null ? reqDto.getAuditReason() : "无";

            String systemPrompt = promptLoader.renderSystem(NovelAiPromptKey.AUDIT_RULE_EXTRACT)
                    + "\n\n" + auditRuleConverter.getFormat();
            Map<String, Object> userVars = new HashMap<>();
            userVars.put("contentText", content);
            userVars.put("auditStatusText", statusStr);
            userVars.put("auditReason", reason);
            String userPrompt = promptLoader.renderUser(NovelAiPromptKey.AUDIT_RULE_EXTRACT, userVars);

            AuditRuleAiOutput aiOutput = structuredOutputInvoker.invoke(
                    chatClient, systemPrompt, userPrompt, auditRuleConverter, "audit-rule-extract");

            long duration = System.currentTimeMillis() - startTime;
            log.info("AI提取审核经验规则响应，耗时: {}ms, aiOutput: {}", duration, aiOutput);

            return RestResp.ok(buildAuditRuleResp(aiOutput, statusStr, reason, content));
        } catch (Exception e) {
            log.error("AI提取审核经验规则异常", e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI提取服务暂时不可用");
        }
    }

    /**
     * 将 AI 润色输出转换为业务响应 DTO，字段缺失时降级成"原文 + 说明"。
     */
    private TextPolishRespDto buildTextPolishResp(TextPolishAiOutput aiOutput, String originalText) {
        TextPolishRespDto resp = new TextPolishRespDto();
        if (aiOutput != null && aiOutput.polishedText() != null && !aiOutput.polishedText().isBlank()) {
            resp.setPolishedText(aiOutput.polishedText());
            resp.setExplanation(aiOutput.explanation() != null ? aiOutput.explanation() : "自动润色");
        } else {
            resp.setPolishedText(originalText);
            resp.setExplanation("AI 未返回有效润色结果，已返回原文");
        }
        return resp;
    }

    /**
     * 将 AI 判例规则抽取输出转换为业务 DTO，字段缺失时用原始审核信息兜底。
     */
    private AuditRuleRespDto buildAuditRuleResp(AuditRuleAiOutput aiOutput,
                                                String statusStr,
                                                String auditReason,
                                                String sourceContent) {
        String violationLabel = (aiOutput != null && aiOutput.violationLabel() != null
                && !aiOutput.violationLabel().isBlank())
                ? aiOutput.violationLabel()
                : ("通过".equals(statusStr) ? "合规内容" : "违规内容");
        String keySnippet = (aiOutput != null && aiOutput.keySnippet() != null
                && !aiOutput.keySnippet().isBlank())
                ? aiOutput.keySnippet()
                : (sourceContent.length() > 100 ? sourceContent.substring(0, 100) : sourceContent);
        String auditRule = (aiOutput != null && aiOutput.auditRule() != null
                && !aiOutput.auditRule().isBlank())
                ? aiOutput.auditRule()
                : auditReason;

        AuditRuleRespDto resp = new AuditRuleRespDto();
        resp.setViolationLabel(violationLabel);
        resp.setKeySnippet(keySnippet);
        resp.setAuditRule(auditRule);
        return resp;
    }

    /** null 归一成空串，避免 PromptTemplate 渲染占位符失败。 */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
