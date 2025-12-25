package com.novel.book.controller.inner;

import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.service.BookReadService;
import com.novel.book.service.BookSearchService;
import com.novel.book.service.BookAuthorService;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "InnerBookController", description = "内部调用-小说模块")
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_BOOK_URL_PREFIX)
@RequiredArgsConstructor
public class InnerBookController {

    private final BookSearchService bookSearchService;
    private final BookAuthorService bookAuthorService;
    private final BookReadService bookReadService;

    /**
     * 查询下一批保存到 ES 中的小说列表
     */
    @Operation(summary = "查询下一批保存到 ES 中的小说列表")
    @PostMapping("listNextEsBooks")
    RestResp<List<BookEsRespDto>> listNextEsBooks(@Parameter(description = "已查询的最大小说ID") @RequestBody Long maxBookId) {

        return bookSearchService.listNextEsBooks(maxBookId);
    }

    /**
     * 根据 ID 获取 ES 书籍数据（新增）
     */
    @GetMapping("/getEsBookById")
    public RestResp<BookEsRespDto> getEsBookById(@RequestParam("bookId") Long bookId) {
        return bookSearchService.getEsBookById(bookId);
    }



    /**
     * 批量查询小说信息
     */
    @Operation(summary = "批量查询小说信息")
    @PostMapping("listBookInfoByIds")
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(@RequestBody List<Long> bookIds) {

        return bookSearchService.listBookInfoByIds(bookIds);
    }

    /**
     * 批量查询小说信息（用于书架，不过滤审核状态）
     */
    @Operation(summary = "批量查询小说信息（用于书架）")
    @PostMapping("listBookInfoByIdsForBookshelf")
    RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(@RequestBody List<Long> bookIds) {
        return bookSearchService.listBookInfoByIdsForBookshelf(bookIds);
    }

    /**
     * 小说发布接口
     */
    @Operation(summary = "小说发布接口")
    @PostMapping("publishBook")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {

        return bookAuthorService.saveBook(dto);
    }

    /**
     * 更新小说接口
     */
    @Operation(summary = "更新小说接口")
    @PostMapping("updateBook")
    public RestResp<Void> updateBook(@Valid @RequestBody BookUptReqDto dto) {
        return bookAuthorService.updateBook(dto);
    }

    @Operation(summary = "删除小说接口")
    @PostMapping("deleteBook")
    public RestResp<Void> deleteBook(@Valid @RequestBody BookDelReqDto dto) {
        return bookAuthorService.deleteBook(dto);
    }

    /**
     * 小说章节发布接口
     */
    @Operation(summary = "小说章节发布接口")
    @PostMapping("publishBookChapter")
    public RestResp<Void> publishBookChapter(@Valid @RequestBody ChapterAddReqDto dto) {

        return bookAuthorService.saveBookChapter(dto);
    }


    /**
     * 小说发布列表查询接口
     */
    @Operation(summary = "小说发布列表查询接口")
    @PostMapping("listPublishBooks")
    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(@RequestBody BookPageReqDto dto) {

        return bookAuthorService.listAuthorBooks(dto);
    }

    /**
     * 小说章节发布列表查询接口
     */
    @Operation(summary = "小说章节发布列表查询接口")
    @PostMapping("listPublishBookChapters")
    public RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(@RequestBody ChapterPageReqDto dto) {

        return bookAuthorService.listBookChapters(dto);
    }

    @Operation(summary = "删除章节")
    @PostMapping("deleteBookChapter")
    public RestResp<Void> deleteBookChapter(@Valid @RequestBody ChapterDelReqDto dto) {

        return bookAuthorService.deleteBookChapter(dto);
    }

    @Operation(summary = "获取单个章节详情")
    @GetMapping("getBookChapter")
    public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {

        return bookAuthorService.getBookChapter(bookId, chapterNum);
    }

    @Operation(summary = "保存对章节的修改")
    @PutMapping("updateBookChapter")
    public RestResp<Void> updateBookChapter(@RequestBody ChapterUptReqDto dto) {

        return bookAuthorService.updateBookChapter(dto);
    }

    @Operation(summary = "获取书籍详情（用于编辑，不过滤审核状态）")
    @GetMapping("getBookByIdForAuthor")
    public RestResp<BookInfoRespDto> getBookByIdForAuthor(
            @Parameter(description = "书籍ID") @RequestParam("bookId") Long bookId,
            @Parameter(description = "作者ID") @RequestParam("authorId") Long authorId) {
        return bookAuthorService.getBookByIdForAuthor(bookId, authorId);
    }


    /**
     * 发表评论接口
     */
    @Operation(summary = "发表评论接口")
    @PostMapping("publishComment")
    public RestResp<Void> publishComment(@Valid @RequestBody BookCommentReqDto dto) {
        return bookReadService.saveComment(dto);
    }

    /**
     * 修改评论接口
     */
    @Operation(summary = "修改评论接口")
    @PostMapping("updateComment")
    public RestResp<Void> updateComment(@Valid @RequestBody BookCommentReqDto dto) {
        return bookReadService.updateComment(dto);
    }

    /**
     * 删除评论接口
     */
    @Operation(summary = "删除评论接口")
    @PostMapping("deleteComment")
    public RestResp<Void> deleteComment(@RequestBody BookCommentReqDto dto) {
        return bookReadService.deleteComment(dto);
    }


}
