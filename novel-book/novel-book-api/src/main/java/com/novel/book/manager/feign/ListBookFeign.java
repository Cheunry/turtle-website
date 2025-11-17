package com.novel.book.manager.feign;

import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.List;


// 1. 接口干净
@FeignClient(
        value = "novel-book-service",
        fallback = ListBookFeignFallback.class   // 指向外部独立类
)
public interface ListBookFeign {

    @PostMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX + "/listBookInfoByIds")
    RestResp<List<BookInfoRespDto>> listBookInfoById(@RequestBody List<Long> bookIds);
}

