package com.novel.search.controller.front;

import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.search.service.SearchService;
import com.novel.search.task.AllBookToEsTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SearchController", description = "前端门户-搜索模块")
@RequestMapping(ApiRouterConsts.API_FRONT_SEARCH_URL_PREFIX)
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final AllBookToEsTask allBookToEsTask;

    /*
        前端代码中定义了 searchBooks 方法用于搜索小说，对应的后端接口路径为：
        接口路径: /front/search/books
        请求方式: GET
        所在文件: src/api/book.js
     */
    @Operation(summary = "小说搜索接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listHomeBooks(@Parameter BookSearchReqDto bookSearchReqDto) {

        return searchService.searchBooks(bookSearchReqDto);
    }

    /** 临时手动触发全量同步的接口
     *  执行只需打开网址：<a href="http://localhost:8080/api/front/search/sync/all">...</a>
     */
    @GetMapping("sync/all")
    public RestResp<String> syncAll() {
        allBookToEsTask.saveToEs();
        return RestResp.ok("全量同步任务已触发，请观察后台日志");
    }



}
