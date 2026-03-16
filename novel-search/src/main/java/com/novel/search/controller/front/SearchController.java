package com.novel.search.controller.front;

import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.search.service.SearchService;
import com.novel.search.config.AllBookToEsTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Tag(name = "SearchController", description = "前端门户-搜索模块")
@RequestMapping(ApiRouterConsts.API_FRONT_SEARCH_URL_PREFIX)
@RestController
@RequiredArgsConstructor
@Slf4j
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

    /** 测试接口，用于验证服务是否正常
     */
    @GetMapping("test")
    public RestResp<String> test() {
        log.info(">>> ========== test 方法被调用 ==========");
        System.out.println(">>> ========== test 方法被调用 (System.out) ==========");
        return RestResp.ok("搜索服务正常，当前时间: " + System.currentTimeMillis());
    }

    /** 临时手动触发全量同步的接口
     *  执行只需打开网址：<a href="http://localhost:8888/api/front/search/sync/all">...</a>
     *  注意：此接口会异步执行全量同步任务，立即返回响应，实际同步任务在后台执行
     */
    @GetMapping("sync/all")
    public RestResp<String> syncAll() {
        log.info(">>> ========== syncAll 方法被调用 ==========");
        System.out.println(">>> ========== syncAll 方法被调用 (System.out) ==========");
        try {
            log.info(">>> 收到全量同步请求，开始异步执行同步任务");
            System.out.println(">>> 收到全量同步请求，开始异步执行同步任务 (System.out)");
            // 异步执行同步任务，避免HTTP请求超时
            CompletableFuture.runAsync(() -> {
                try {
                    log.info(">>> 异步任务开始执行全量同步");
                    allBookToEsTask.saveToEs();
                    log.info(">>> 异步任务全量同步执行完成");
                } catch (Exception e) {
                    log.error(">>> 异步任务执行全量同步时发生异常", e);
                }
            });
            return RestResp.ok("全量同步任务已触发，正在后台执行，请观察后台日志");
        } catch (Exception e) {
            log.error(">>> 触发全量同步任务时发生异常", e);
            return RestResp.ok("全量同步任务触发失败，请查看后台日志: " + e.getMessage());
        }
    }



}
