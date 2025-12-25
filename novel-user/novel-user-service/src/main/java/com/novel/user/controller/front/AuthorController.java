package com.novel.user.controller.front;

import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.service.AuthorService;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.ErrorCodeEnum;
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
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Tag(name = "AuthorController", description = "作者模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequiredArgsConstructor
@Slf4j
@RefreshScope
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX)
public class AuthorController {

    @Value("${novel.audit.enable:true}")
    private Boolean auditEnable;

    private final AuthorService authorService;

    /**
     * 校验用户是否是作家
     * 如果返回 null，则表示不是作家，前端会跳转到注册页面
     */
    @Operation(summary = "查询作家的状态")
    @GetMapping("status")
    public RestResp<AuthorInfoDto> getStatus() {

        return authorService.getStatus(UserHolder.getUserId());
    }

    /**
     * 作家注册接口
     */
    @Operation(summary = "作家注册接口")
    @PostMapping("register")
    public RestResp<Void> register(@Valid @RequestBody AuthorRegisterReqDto dto) {

        dto.setUserId(UserHolder.getUserId());
        return authorService.authorRegister(dto);
    }

    /**
     * 发布书籍接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 BookAddListener 消费者中异步处理
     */
    @Operation(summary = "发布书籍接口")
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        String penName = UserHolder.getAuthorPenName();
        if (penName == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.publishBook(authorId, penName, dto, auditEnable);
    }

    /**
     * 更新书籍接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 BookUpdateListener 消费者中异步处理
     */
    @Operation(summary = "更新书籍接口")
    @PutMapping("book/{bookId}")
    public RestResp<Void> updateBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody BookUptReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.updateBook(authorId, bookId, dto, auditEnable);
    }

    /**
     * 删除书籍接口
     */
    @Operation(summary = "删除书籍接口")
    @DeleteMapping("book/{bookId}")
    public RestResp<Void> deleteBook(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId) {
        return authorService.deleteBook(bookId);
    }

    /**
     * 小说章节发布接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 ChapterSubmitListener 消费者中异步处理
     */
    @Operation(summary = "小说章节发布接口")
    @PostMapping("book/chapter/{bookId}")
    public RestResp<Void> publishBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Valid @RequestBody ChapterAddReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.publishBookChapter(authorId, bookId, dto, auditEnable);
    }

    /**
     * 获取作者书籍列表接口
     */
    @Operation(summary = "获取作者书籍列表接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(@ParameterObject BookPageReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.listBooks(authorId, dto);
    }


    /**
     * 小说章节发布列表查询接口
     */
    @Operation(summary = "小说章节发布列表查询接口")
    @GetMapping("book/chapters/{bookId}")
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @ParameterObject PageReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.listBookChapters(authorId, bookId, dto);
    }

    /**
     * 获取单个章节详情接口
     */
    @Operation(summary = "获取单个章节详情")
    @GetMapping("book/chapter/{bookId}/{chapterNum}")
    public RestResp<BookChapterRespDto> getBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return authorService.getBookChapter(bookId, chapterNum);
    }

    /**
     * 获取书籍详情接口（用于编辑，不过滤审核状态）
     */
    @Operation(summary = "获取书籍详情（用于编辑）")
    @GetMapping("book/{bookId}")
    public RestResp<BookInfoRespDto> getBookById(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId) {
        return authorService.getBookById(bookId);
    }

    /**
     * 更新章节接口（异步化版本：发送MQ后立即返回）
     * 所有数据库操作都在 ChapterSubmitListener 消费者中异步处理
     */
    @Operation(summary = "保存对更新章节的修改")
    @PutMapping("book/chapter_update/{bookId}/{chapterNum}")
    public RestResp<Void> updateBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum,
            @Valid @RequestBody ChapterUptReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.updateBookChapter(authorId, bookId, chapterNum, dto, auditEnable);
    }

    /**
     *  删除章节接口
     */
    @Operation(summary = "删除章节")
    @PostMapping("book/chapter/delete/{bookId}/{chapterNum}")
    public RestResp<Void> deleteBookChapter(
            @Parameter(description = "小说ID") @PathVariable("bookId") Long bookId,
            @Parameter(description = "章节号") @PathVariable("chapterNum") Integer chapterNum) {
        return authorService.deleteBookChapter(bookId, chapterNum);
    }

    /* ************************作家消息相关接口************************* */

    @Operation(summary = "获取作家消息列表")
    @PostMapping("message/list")
    public RestResp<PageRespDto<MessageRespDto>> listAuthorMessages(
        @Parameter(description = "分页参数") @RequestBody MessagePageReqDto pageReqDto
        ) {
        return authorService.listAuthorMessages(pageReqDto);
    }

    @Operation(summary = "获取作家未读消息数量")
    @GetMapping("message/unread_count")
    public RestResp<Long> getAuthorUnReadCount() {
        return authorService.getAuthorUnReadCount();
    }

    @Operation(summary = "标记作家消息为已读")
    @PutMapping("message/read/{id}")
    public RestResp<Void> readAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return authorService.readAuthorMessage(id);
    }

    @Operation(summary = "删除作家消息")
    @DeleteMapping("message/{id}")
    public RestResp<Void> deleteAuthorMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return authorService.deleteAuthorMessage(id);
    }

    @Operation(summary = "批量标记作家消息为已读")
    @PutMapping("message/batch_read")
    public RestResp<Void> batchReadAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return authorService.batchReadAuthorMessages(ids);
    }

    @Operation(summary = "批量删除作家消息")
    @PostMapping("message/batch_delete")
    public RestResp<Void> batchDeleteAuthorMessages(@RequestBody java.util.List<Long> ids) {
        return authorService.batchDeleteAuthorMessages(ids);
    }

    @Operation(summary = "全部标记作家消息为已读")
    @PutMapping("message/all_read")
    public RestResp<Void> allReadAuthorMessages() {
        return authorService.allReadAuthorMessages();
    }

    @Operation(summary = "全部删除作家消息")
    @PostMapping("message/all_delete")
    public RestResp<Void> allDeleteAuthorMessages() {
        return authorService.allDeleteAuthorMessages();
    }

    /* ************************ AI 积分消耗相关接口 ************************* */

    /**
     * AI审核（先扣分后服务，服务失败自动回滚积分）
     * 消耗：1点/次
     */
    @Operation(summary = "AI审核（先扣分后服务）")
    @PostMapping("ai/audit")
    public RestResp<Object> audit(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.audit(authorId, dto);
    }

    /**
     * AI润色（先扣分后服务，服务失败自动回滚积分）
     * 消耗：10点/次
     */
    @Operation(summary = "AI润色（先扣分后服务）")
    @PostMapping("ai/polish")
    public RestResp<Object> polish(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.polish(authorId, dto);
    }

    /**
     * AI封面提示词生成（不扣积分，仅生成提示词）
     */
    @Operation(summary = "AI封面提示词生成（不扣积分）")
    @PostMapping("ai/cover-prompt")
    public RestResp<String> generateCoverPrompt(@RequestBody BookCoverReqDto reqDto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.generateCoverPrompt(authorId, reqDto);
    }

    /**
     * AI封面生成（先扣分后服务，服务失败自动回滚积分）
     * 消耗：100点/次
     */
    @Operation(summary = "AI封面生成（先扣分后服务）")
    @PostMapping("ai/cover")
    public RestResp<Object> generateCover(@RequestBody AuthorPointsConsumeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        
        return authorService.generateCover(authorId, dto);
    }

}
