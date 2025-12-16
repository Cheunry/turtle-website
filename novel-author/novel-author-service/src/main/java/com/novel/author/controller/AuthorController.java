package com.novel.author.controller;

import com.novel.author.dto.req.AuthorRegisterReqDto;
import com.novel.author.feign.BookFeignManager;
import com.novel.author.service.AuthorInfoService;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import com.novel.book.dto.req.BookUptReqDto;
import com.novel.book.dto.req.BookDelReqDto;

@Tag(name = "AuthorController", description = "作者模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX)
public class AuthorController {

    private final AuthorInfoService authorInfoService;
    private final BookFeignManager bookFeignManager;

    /**
     * 校验用户是否是作家
     * 如果返回 null，则表示不是作家，前端会跳转到注册页面
     */
    @Operation(summary = "查询作家的状态")
    @GetMapping("status")
    public RestResp<Integer> getStatus() {

        return authorInfoService.getStatus(UserHolder.getUserId());
    }

    /**
     * 作家注册接口
     */
    @Operation(summary = "作家注册接口")
    @PostMapping("register")
    public RestResp<Void> register(@Valid @RequestBody AuthorRegisterReqDto dto) {

        dto.setUserId(UserHolder.getUserId());
        return authorInfoService.authorRegister(dto);
    }


    /**
     * 发布书籍接口
     */
    @Operation(summary = "发布书籍接口")
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {

        return bookFeignManager.publishBook(dto);
    }

    /**
     * 更新书籍接口
     */
    @Operation(summary = "更新书籍接口")
    @PutMapping("book/{bookId}")
    public RestResp<Void> updateBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody BookUptReqDto dto) {
        
        dto.setBookId(bookId);
        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeignManager.updateBook(dto);
    }

    /**
     * 删除书籍接口
     */
    @Operation(summary = "删除书籍接口")
    @DeleteMapping("book/{bookId}")
    public RestResp<Void> deleteBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId) {
        BookDelReqDto dto = new BookDelReqDto();
        dto.setBookId(bookId);
        return bookFeignManager.deleteBook(dto);
    }

    /**
     * 小说章节发布接口
     */
    @Operation(summary = "小说章节发布接口")
    @PostMapping("book/chapter/{bookId}")
    public RestResp<Void> publishBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody ChapterAddReqDto dto) {
        dto.setAuthorId(UserHolder.getAuthorId());
        dto.setBookId(bookId);
        return bookFeignManager.publishBookChapter(dto);
    }

    /**
     * 获取作者书籍列表接口
     */
    @Operation(summary = "获取作者书籍列表接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(@ParameterObject BookPageReqDto dto) {

        dto.setAuthorId(UserHolder.getAuthorId());

        return bookFeignManager.listPublishBooks(dto);
    }


    /**
     * 小说章节发布列表查询接口
     */
    @Operation(summary = "小说章节发布列表查询接口")
    @GetMapping("book/chapters/{bookId}")
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @ParameterObject PageReqDto dto) {
        ChapterPageReqDto chapterPageReqReqDto = new ChapterPageReqDto();
        chapterPageReqReqDto.setBookId(bookId);
        chapterPageReqReqDto.setPageNum(dto.getPageNum());
        chapterPageReqReqDto.setPageSize(dto.getPageSize());
        chapterPageReqReqDto.setAuthorId(UserHolder.getAuthorId());     // 设置 AuthorId，用于后续权限校验
        return bookFeignManager.listPublishBookChapters(chapterPageReqReqDto);
    }

    /**
     * 获取单个章节详情接口
     */
    @Operation(summary = "获取单个章节详情")
    @GetMapping("book/chapter/{bookId}/{chapterNum}")
    public RestResp<BookChapterRespDto> getBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return bookFeignManager.getBookChapter(bookId, chapterNum);
    }

    /**
     * 更新章节接口
     */
    @Operation(summary = "保存对更新章节的修改")
    @PutMapping("book/chapter_update/{bookId}/{chapterNum}")
    public RestResp<Void> updateBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum,
            @Valid @RequestBody ChapterUptReqDto dto) {
        dto.setBookId(bookId);
        dto.setOldChapterNum(chapterNum);
        return bookFeignManager.updateBookChapter(dto);
    }

    /**
     *  删除章节接口
     */
    @Operation(summary = "删除章节")
    @PostMapping("book/chapter/delete/{bookId}/{chapterNum}")
    public RestResp<Void> deleteBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        ChapterDelReqDto dto = new ChapterDelReqDto();
        dto.setBookId(bookId);
        dto.setChapterNum(chapterNum);
        return bookFeignManager.deleteBookChapter(dto);
    }

}