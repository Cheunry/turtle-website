package com.novel.book.manager.feign;

import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.List;


@Component
@FeignClient(value = "novel-book-service", fallback = BookFeign.BookFeignFallback.class)
public interface BookFeign {

    /**
     * 批量查询小说信息
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listBookInfoByIds")
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds);

    /**
     * 小说发布接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/publishBook")
    RestResp<Void> publishBook(BookAddReqDto dto);

    /**
     * 小说发布列表查询接口
     */
    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listPublishBooks")
    RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto);



    @Component
    class BookFeignFallback implements BookFeign {

        @Override
        public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {

            return RestResp.ok(new ArrayList<>(0));
        }

        @Override
        public RestResp<Void> publishBook(BookAddReqDto dto) {

            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR);
        }

        @Override
        public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {

            return RestResp.ok(PageRespDto.of(dto.getPageNum(), dto.getPageSize(),
                    0, new ArrayList<>(0)));
        }

    }


}
