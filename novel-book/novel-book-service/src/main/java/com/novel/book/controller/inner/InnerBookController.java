package com.novel.book.controller.inner;

import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.service.BookSearchService;
import com.novel.book.service.BookAuthorService;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 小说发布列表查询接口
     */
    @Operation(summary = "小说发布列表查询接口")
    @PostMapping("listPublishBooks")
    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(@RequestBody BookPageReqDto dto) {
        return bookAuthorService.listAuthorBooks(dto);
    }
}
