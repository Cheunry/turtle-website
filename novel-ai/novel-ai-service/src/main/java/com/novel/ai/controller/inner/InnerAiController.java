package com.novel.ai.controller.inner;

import com.novel.ai.dto.req.AuditExperienceUpsertReqDto;
import com.novel.ai.dto.req.AuditRuleReqDto;
import com.novel.ai.dto.resp.AuditExperienceUpsertRespDto;
import com.novel.ai.dto.resp.AuditRuleRespDto;
import com.novel.ai.rag.AuditExperienceIndexer;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.ai.service.ImageService;
import com.novel.ai.service.TextService;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "InnerAiController", description = "内部服务-AI模块")
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX)
@RequiredArgsConstructor
public class InnerAiController {

    private final TextService textService;
    private final ImageService imageService;
    private final AuditExperienceIndexer auditExperienceIndexer;

    /**
     * 审核书籍内容
     */
    @Operation(summary = "审核书籍内容")
    @PostMapping("/audit/book")
    public RestResp<BookAuditRespDto> auditBook(@RequestBody BookAuditReqDto req) {
        return textService.auditBook(req);
    }

    /**
     * 审核章节内容
     */
    @Operation(summary = "审核章节内容")
    @PostMapping("/audit/chapter")
    public RestResp<ChapterAuditRespDto> auditChapter(@RequestBody ChapterAuditReqDto req) {
        return textService.auditChapter(req);
    }

    /**
     * 获取图片生成提示词
     */
    @Operation(summary = "获取图片生成提示词")
    @PostMapping("/generate/image/prompt")
    public RestResp<String> generateImagePrompt(@RequestBody BookCoverReqDto req) {
        return textService.getBookCoverPrompt(req);
    }

    /**
     * 文本润色
     */
    @Operation(summary = "文本润色")
    @PostMapping("/polish")
    public RestResp<com.novel.ai.dto.resp.TextPolishRespDto> polishText(@RequestBody com.novel.ai.dto.req.TextPolishReqDto req) {
        return textService.polishText(req);
    }

    /**
     * 根据提示词生成图片
     */
    @Operation(summary = "根据提示词生成图片")
    @PostMapping("/generate/image")
    public RestResp<String> generateImage(@RequestParam("prompt") String prompt) {
        return imageService.generateImage(prompt);
    }

    /**
     * 提取审核经验规则
     */
    @Operation(summary = "提取审核经验规则")
    @PostMapping("/audit/extractRule")
    public RestResp<AuditRuleRespDto> extractAuditRule(@RequestBody AuditRuleReqDto req) {
        return textService.extractAuditRule(req);
    }

    /**
     * 审核经验判例写入向量库（单条或批量）。
     * <p>
     * <b>零耦合</b>：novel-ai 模块只接收外部推送的判例字段，不反向拉取任何业务数据。
     * 调用方可以是：
     * <ul>
     *     <li>运维手动 curl（从 DB 拷字段出来写判例）；</li>
     *     <li>将来的人审后台（人审完成后同步一条到 RAG）；</li>
     *     <li>novel-ai 自己的在线审核管线（审完不通过自动沉淀，见后续阶段）。</li>
     * </ul>
     * 幂等语义：同一个 {@code auditId} 再次写入会覆盖旧向量。
     */
    @Operation(summary = "审核经验判例批量写入（向量库 upsert）")
    @PostMapping("/audit-experience/upsert")
    public RestResp<AuditExperienceUpsertRespDto> upsertAuditExperience(
            @RequestBody List<AuditExperienceUpsertReqDto> experiences,
            @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        AuditExperienceIndexer.IndexResult result = auditExperienceIndexer.upsert(experiences, dryRun);
        return RestResp.ok(new AuditExperienceUpsertRespDto(
                result.getTotalScanned(),
                result.getAccepted(),
                result.getSkipped(),
                result.getFailed(),
                dryRun));
    }
}
