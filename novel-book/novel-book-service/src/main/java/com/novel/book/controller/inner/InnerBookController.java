package com.novel.book.controller.inner;

import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
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

    /**
     * 批量查询小说信息
     */
    @Operation(summary = "批量查询小说信息")
    @PostMapping("listBookInfoByIds")
    RestResp<List<BookInfoRespDto>> listBookInfoByIds(@RequestBody List<Long> bookIds) {
        return bookSearchService.listBookInfoByIds(bookIds);
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
    public RestResp<BookChapterRespDto> getBookChapter(Long id) {
        return bookAuthorService.getBookChapter(id);
    }

    @Operation(summary = "保存对章节的修改")
    @PutMapping("updateBookChapter")
    public RestResp<Void> updateBookChapter(@RequestBody ChapterUptReqDto dto) {
        return bookAuthorService.updateBookChapter(dto);
    }

}
