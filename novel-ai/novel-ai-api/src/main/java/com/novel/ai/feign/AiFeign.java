package com.novel.ai.feign;

import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@Component
@FeignClient(value = "novel-ai-service", fallback = AiFeign.AiFeignFallback.class)
public interface AiFeign {

    /**
     * 审核书籍内容
     */
    @PostMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX + "/audit/book")
    RestResp<BookAuditRespDto> auditBook(@RequestBody BookAuditReqDto req);

    /**
     * 审核章节内容
     */
    @PostMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX + "/audit/chapter")
    RestResp<ChapterAuditRespDto> auditChapter(@RequestBody ChapterAuditReqDto req);

    /**
     * 获取小说封面提示词
     */
    @PostMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX + "/generate/image/prompt")
    RestResp<String> getBookCoverPrompt(@RequestBody BookCoverReqDto req);

    /**
     * 文本润色
     */
    @PostMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX + "/polish")
    RestResp<TextPolishRespDto> polishText(@RequestBody TextPolishReqDto req);

    /**
     * 根据提示词生成图片
     */
    @PostMapping(ApiRouterConsts.API_INNER_AI_URL_PREFIX + "/generate/image")
    RestResp<String> generateImage(@RequestParam("prompt") String prompt);


    @Component
    class AiFeignFallback implements AiFeign {

        @Override
        public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(ErrorCodeEnum.AI_AUDIT_SERVICE_ERROR);
        }

        @Override
        public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(ErrorCodeEnum.AI_AUDIT_SERVICE_ERROR);
        }

        @Override
        public RestResp<String> getBookCoverPrompt(BookCoverReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(ErrorCodeEnum.AI_COVER_TEXT_SERVICE_ERROR);
        }

        @Override
        public RestResp<TextPolishRespDto> polishText(TextPolishReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        @Override
        public RestResp<String> generateImage(String prompt) {
            // 降级处理：返回失败响应
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
}