package com.novel.ai.feign;

import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


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

    @Component
    class AiFeignFallback implements AiFeign {

        @Override
        public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(com.novel.common.constant.ErrorCodeEnum.AI_AUDIT_SERVICE_ERROR);
        }

        @Override
        public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto req) {
            // 降级处理：返回失败响应
            return RestResp.fail(com.novel.common.constant.ErrorCodeEnum.AI_AUDIT_SERVICE_ERROR);
        }
    }
}