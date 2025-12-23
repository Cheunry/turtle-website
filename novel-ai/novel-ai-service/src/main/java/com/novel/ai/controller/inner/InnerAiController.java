package com.novel.ai.controller.inner;

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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "InnerAiController", description = "内部服务-AI模块")
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX)
@RequiredArgsConstructor
public class InnerAiController {

    private final TextService textService;
    private final ImageService imageService;

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
    public RestResp<String> generateImage(@org.springframework.web.bind.annotation.RequestParam("prompt") String prompt) {
        return imageService.generateImage(prompt);
    }
}