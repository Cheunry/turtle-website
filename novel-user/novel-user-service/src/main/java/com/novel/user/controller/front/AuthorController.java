package com.novel.user.controller.front;

import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.service.AuthorInfoService;
import com.novel.user.service.MessageService;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.service.impl.MessageServiceImpl;
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
    private final MessageService messageService; // Add field

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

    /* ************************作家消息相关接口************************* */

    @Operation(summary = "获取作家消息列表")
    @PostMapping("message/list")
    public RestResp<PageRespDto<MessageRespDto>> listAuthorMessages(
        @Parameter(description = "分页参数") @RequestBody MessagePageReqDto pageReqDto
        ) {
        // 明确指定只查询作者消息（receiver_type=2），避免与普通用户消息混淆
        pageReqDto.setReceiverType(2);
        // 如果未指定消息类型，默认只查作家相关的消息（类型2:作家助手/审核）
        // 但允许前端通过busType进一步筛选（如：BOOK_AUDIT, CHAPTER_AUDIT, BOOK_COVER等）
        if (pageReqDto.getMessageType() == null) {
            pageReqDto.setMessageType(2);
        }
        return messageService.listMessages(pageReqDto);
    }

    @Operation(summary = "获取作家未读消息数量")
    @GetMapping("message/unread_count")
    public RestResp<Long> getAuthorUnReadCount() {
        // 调用专门的方法统计作者消息（receiver_type=2）
        MessageServiceImpl messageServiceImpl = (MessageServiceImpl) messageService;
        return messageServiceImpl.getUnReadCountByReceiverType(2, 2);
    }

    @Operation(summary = "标记作家消息为已读")
    @PutMapping("message/read/{id}")
    public RestResp<Void> readAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.readMessage(id);
    }

    @Operation(summary = "删除作家消息")
    @DeleteMapping("message/{id}")
    public RestResp<Void> deleteAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.deleteMessage(id);
    }

    @Operation(summary = "批量标记作家消息为已读")
    @PutMapping("message/batch_read")
    public RestResp<Void> batchReadAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return messageService.batchReadMessages(2, ids);
    }

    @Operation(summary = "批量删除作家消息")
    @PostMapping("message/batch_delete")
    public RestResp<Void> batchDeleteAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return messageService.batchDeleteMessages(2, ids);
    }

    @Operation(summary = "全部标记作家消息为已读")
    @PutMapping("message/all_read")
    public RestResp<Void> allReadAuthorMessages() {
        return messageService.allReadMessages(2);
    }

    @Operation(summary = "全部删除作家消息")
    @PostMapping("message/all_delete")
    public RestResp<Void> allDeleteAuthorMessages() {
        return messageService.allDeleteMessages(2);
    }

}
