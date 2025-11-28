package com.novel.book.controller.front;


import com.novel.book.dto.resp.*;
import com.novel.book.service.BookListSearchService;
import com.novel.book.service.BookReadService;
import com.novel.book.service.BookSearchService;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "FrontBookController", description = "前台门户-小说模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_BOOK_URL_PREFIX)
@RequiredArgsConstructor
public class FrontBookController {

    private final BookSearchService bookSearchService;
    private final BookListSearchService bookListSearchService;
    private final BookReadService bookReadService;

    /**
     * 小说分类列表查询接口
     */
    @Operation(summary = "小说分类列表查询接口")
    @GetMapping("category/list")
    public RestResp<List<BookCategoryRespDto>> listCategory(
            @Parameter(description = "作品方向", required = true) Integer workDirection) {
        return bookListSearchService.listCategory(workDirection);
    }

    /**
     * 小说点击榜查询接口
     */
    @Operation(summary = "小说点击榜查询接口")
    @GetMapping("visit_rank")
    public RestResp<List<BookRankRespDto>> listVisitRankBooks() {
        return bookListSearchService.listVisitRankBooks();
    }

    /**
     * 小说新书榜查询接口
     */
    @Operation(summary = "小说新书榜查询接口")
    @GetMapping("newest_rank")
    public RestResp<List<BookRankRespDto>> listNewestRankBooks() {
        return bookListSearchService.listNewestRankBooks();
    }

    /**
     * 小说更新榜查询接口
     */
    @Operation(summary = "小说更新榜查询接口")
    @GetMapping("update_rank")
    public RestResp<List<BookRankRespDto>> listUpdateRankBooks() {
        return bookListSearchService.listUpdateRankBooks();
    }

    /**
     * 小说章节目录查询接口
     */
    @Operation(summary = "小说章节目录查询接口")
    @GetMapping("chapter/list")
    public RestResp<List<BookChapterRespDto>> getChapterList(
            @Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return bookReadService.getBookChapter(bookId);
    }

    /**
     * 小说内容相关信息查询接口
     */
    @Operation(summary = "小说内容相关信息查询接口")
    @GetMapping("content/{chapterId}")
    public RestResp<BookContentAboutRespDto> getBookContentAbout(
            @Parameter(description = "章节ID") @PathVariable("chapterId") Long chapterId) {
        return bookReadService.getBookContentAbout(chapterId);
    }

    /**
     * 获取上一章节ID接口
     */
    @Operation(summary = "获取上一章节ID接口")
    @GetMapping("pre_chapter_id/{bookId}/{chapterNum}")
    public RestResp<Long> getPreChapterId(
            @Parameter(description = "书籍ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return bookReadService.getPreChapterId(bookId, chapterNum);
    }

    /**
     * 获取下一章节ID接口
     */
    @Operation(summary = "获取下一章节ID接口")
    @GetMapping("next_chapter_id/{bookId}/{chapterNum}")
    public RestResp<Long> getNextChapterId(
            @Parameter(description = "书籍ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return bookReadService.getNextChapterId(bookId, chapterNum);
    }

    /**
     * 小说信息查询接口（放在最后，因为路径最宽泛）
     */
    @Operation(summary = "小说信息查询接口")
    @GetMapping("{id}")
    public RestResp<BookInfoRespDto> getBookById(
            @Parameter(description = "小说 ID") @PathVariable("id") Long bookId) {
        return bookSearchService.getBookById(bookId);
    }

}
