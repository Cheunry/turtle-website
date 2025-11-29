package com.novel.author.controller;

import com.novel.author.dto.req.AuthorRegisterReqDto;
import com.novel.author.manager.feign.BookFeignManager;
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

@Tag(name = "AuthorController", description = "作者模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX)
public class AuthorController {

    private final AuthorInfoService authorInfoService;
    private final BookFeignManager bookFeignManager;

// 作者账号相关

    /*检查作者状态
            接口路径: GET /author/status
            前端方法: getAuthorStatus()
            功能: 判断当前登录用户是否已经注册成为作者。*/
    /**
     * 校验用户是否是作家
     * 如果返回 null，则表示不是作家，前端会跳转到注册页面
     */
    @Operation(summary = "查询作家的状态")
    @GetMapping("status")
    public RestResp<Integer> getStatus() {

        return authorInfoService.getStatus(UserHolder.getUserId());
    }


    /*注册成为作者
            接口路径: POST /author/register
            前端方法: register(params)
            功能: 普通用户申请成为作者，可能需要提交笔名、简介等信息。*/
    /**
     * 作家注册接口
     *
     */
    @Operation(summary = "作家注册接口")
    @PostMapping("register")
    public RestResp<Void> register(@Valid @RequestBody AuthorRegisterReqDto dto) {

        dto.setUserId(UserHolder.getUserId());
        authorInfoService.authorRegister(dto);

        return RestResp.ok();
    }

// 书籍管理相关


    /*发布书籍
            接口路径: POST /author/book
            前端方法: publishBook(params)
            功能: 创建一本新书。
            建议参数: 书籍名称、分类ID、封面图片路径、简介、标签等。*/
    /**
     * 发布书籍接口
     */
    @Operation(summary = "发布书籍接口")
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {

        return bookFeignManager.publishBook(dto);
    }

    /*发布章节
        接口路径: POST /author/book/chapter/{bookId}
        前端方法: publishChapter(bookId, params)
        功能: 为指定书籍 ID 发布一个新的章节。
        建议参数: 章节标题 (chapterName), 章节内容 (chapterContent), 是否收费 (isVip), 字数等。*/
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

    /*获取作者书籍列表
            接口路径: GET /author/books
            前端方法: listBooks(params)
            功能: 分页展示当前登录作者发布的所有书籍。
            建议参数: page (页码), limit (每页数量)。*/
    /**
     * 获取作者书籍列表接口
     */
    @Operation(summary = "获取作者书籍列表接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(@ParameterObject BookPageReqDto dto) {

        dto.setAuthorId(UserHolder.getAuthorId());

        return bookFeignManager.listPublishBooks(dto);
    }
// 章节管理接口 (编写书籍)

    /*获取书籍章节列表
        接口路径: GET /author/book/chapters/{bookId}
        前端方法: listChapters(bookId, params)
        功能: 获取指定书籍的所有章节列表（通常用于管理页面）。
        建议参数: 分页参数。*/
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
        return bookFeignManager.listPublishBookChapters(chapterPageReqReqDto);
    }

    /*获取单个章节详情 (用于编辑)
        接口路径: GET /author/book/chapter/{id}
        前端方法: getChapter(id)
        功能: 获取特定章节的详细内容，用于在编辑器中回显数据。*/
    @Operation(summary = "获取单个章节详情")
    @GetMapping("book/chapter/{id}")
    public RestResp<BookChapterRespDto> getBookChapter(
            @Parameter(description = "章节ID") @PathVariable("id") Long id) {
        return bookFeignManager.getBookChapter(id);
    }

    /*更新章节
        接口路径: PUT /author/book/chapter/{id}
        前端方法: updateChapter(id, params)
        功能: 保存对已有章节的修改。*/
    @Operation(summary = "保存对更新章节的修改")
    @PutMapping("book/chapter_update/{id}")
    public RestResp<Void> updateBookChapter(
            @Parameter(description = "章节ID") @PathVariable("id") Long id,
            @Valid @RequestBody ChapterUptReqDto dto) {
        dto.setChapterId(id);
        return bookFeignManager.updateBookChapter(dto);
    }

    /*删除章节
        接口路径: POST /author/book/chapter/delete/{id}  (建议修改路径以区分发布)
        前端方法: deleteChapter(id)
        功能: 删除指定章节。*/
    @Operation(summary = "删除章节")
    @PostMapping("book/chapter/delete/{id}")
    public RestResp<Void> deleteBookChapter(
            @Parameter(description = "章节ID") @PathVariable("id") Long id,
            @Valid @RequestBody ChapterDelReqDto dto) {
        dto.setChapterId(id);
        // authorId 会在 Manager 层统一设置，这里设置也可以，但要注意 Manager 的覆盖逻辑
        return bookFeignManager.deleteBookChapter(dto);
    }




}