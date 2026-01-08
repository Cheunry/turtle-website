package com.novel.user.controller.front;

import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.*;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.service.MessageService;
import com.novel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台门户-会员模块
 */
@Slf4j
@Tag(name = "UserController", description = "前台门户-会员模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX)
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MessageService messageService;
    private final BookFeignManager bookFeignManager;

    /* ***********************用户服务基础接口************************* */

    @Operation(summary = "用户注册接口")
    @PostMapping("register")
    public RestResp<UserRegisterRespDto> register(@Valid @RequestBody UserRegisterReqDto dto) {
        return userService.register(dto);
    }

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public RestResp<UserLoginRespDto> login(@Valid @RequestBody UserLoginReqDto dto) {
        log.debug("用户登录请求: {}", dto);
        return userService.login(dto);
    }

    @Operation(summary = "用户登出接口")
    @PostMapping("logout")
    public RestResp<Void> logout(@RequestHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME) String token) {
        return userService.logout(token);
    }

    @Operation(summary = "用户信息查询接口")
    @GetMapping
    public RestResp<UserInfoRespDto> getUserInfo() {
        return userService.getUserInfo(UserHolder.getUserId());
    }

    @Operation(summary = "用户信息修改接口")
    @PutMapping
    public RestResp<Void> updateUserInfo(@Valid @RequestBody UserInfoUptReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return userService.updateUserInfo(dto);
    }

    /* ***********************反馈相关接口************************* */

    @Operation(summary = "用户反馈提交接口")
    @PostMapping("feedback")
    public RestResp<Void> submitFeedback(@RequestBody String content) {
        return userService.saveFeedback(UserHolder.getUserId(), content);
    }

    @Operation(summary = "用户反馈删除接口")
    @DeleteMapping("feedback/{id}")
    public RestResp<Void> deleteFeedback(@Parameter(description = "反馈ID") @PathVariable Long id) {
        return userService.deleteFeedback(UserHolder.getUserId(), id);
    }

    /* ************************书架相关接口************************* */

    @Operation(summary = "查询书架状态接口")
    @GetMapping("bookshelf_status")
    public RestResp<Integer> getBookshelfStatus(@Parameter(description = "小说ID") @RequestParam String bookId) {
        return userService.getBookshelfStatus(UserHolder.getUserId(), bookId);
    }

    @Operation(summary = "加入书架接口")
    @PostMapping("bookshelf")
    public RestResp<Void> addToBookshelf(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return userService.addToBookshelf(UserHolder.getUserId(), bookId);
    }

    @Operation(summary = "查询书架列表接口")
    @GetMapping("bookshelf")
    public RestResp<List<UserBookshelfRespDto>> listBookshelf() {
        return userService.listBookshelf(UserHolder.getUserId());
    }

    @Operation(summary = "更新书架阅读进度接口")
    @PutMapping("bookshelf/process")
    public RestResp<Void> updateBookshelfProcess(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId,
                                                 @Parameter(description = "章节号") @RequestParam("chapterNum") Integer chapterNum) {
        return userService.updatePreChapterId(UserHolder.getUserId(), bookId, chapterNum);
    }

    @Operation(summary = "删除书架中的书籍")
    @DeleteMapping("bookshelf")
    public RestResp<Void> deleteBookshelf(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return userService.deleteBookshelf(UserHolder.getUserId(), bookId);
    }

    /* ************************评论相关接口************************* */

    @Operation(summary = "发表评论接口")
    @PostMapping("comment")
    public RestResp<Void> comment(@Valid @RequestBody BookCommentReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return bookFeignManager.publishComment(dto);
    }

    @Operation(summary = "修改评论接口")
    @PutMapping("comment/{id}")
    public RestResp<Void> updateComment(@Parameter(description = "评论ID") @PathVariable Long id,
                                        String content) {
        BookCommentReqDto dto = new BookCommentReqDto();
        dto.setUserId(UserHolder.getUserId());
        dto.setCommentId(id);
        dto.setCommentContent(content);
        return bookFeignManager.updateComment(dto);
    }

    @Operation(summary = "删除评论接口")
    @DeleteMapping("comment/{id}")
    public RestResp<Void> deleteComment(@Parameter(description = "评论ID") @PathVariable Long id) {
        BookCommentReqDto dto = new BookCommentReqDto();
        dto.setUserId(UserHolder.getUserId());
        dto.setCommentId(id);
        return bookFeignManager.deleteComment(dto);
    }
    


    /* ************************消息相关接口************************* */

    @Operation(summary = "获取用户消息列表")
    @GetMapping("message")
    public RestResp<PageRespDto<MessageRespDto>> listUserMessages(
            @ParameterObject MessagePageReqDto pageReqDto
    ) {
        // 明确指定只查询普通用户消息，避免同时是作者的用户看到作者消息
        pageReqDto.setReceiverType(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER);
        return messageService.listMessages(pageReqDto);
    }

    @Operation(summary = "标记用户消息为已读")
    @PutMapping("message/read/{id}")
    public RestResp<Void> readUserMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.readMessage(id);
    }

    @Operation(summary = "删除用户消息")
    @DeleteMapping("message/{id}")
    public RestResp<Void> deleteUserMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return messageService.deleteMessage(id);
    }

    @Operation(summary = "获取用户未读消息数量")
    @GetMapping("message/unread_count")
    public RestResp<Long> getUserUnReadCount(
            @Parameter(description = "消息类型(可选)") @RequestParam(required = false) Integer type
    ) {
        return messageService.getUnReadCount(type);
    }

    @Operation(summary = "批量标记用户消息为已读")
    @PutMapping("message/batch_read")
    public RestResp<Void> batchReadUserMessages(@RequestBody List<Long> ids) {
        return messageService.batchReadMessages(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER, ids);
    }

    @Operation(summary = "批量删除用户消息")
    @PostMapping("message/batch_delete")
    public RestResp<Void> batchDeleteUserMessages(@RequestBody List<Long> ids) {
        return messageService.batchDeleteMessages(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER, ids);
    }

    @Operation(summary = "全部标记用户消息为已读")
    @PutMapping("message/all_read")
    public RestResp<Void> allReadUserMessages() {
        return messageService.allReadMessages(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER);
    }

    @Operation(summary = "全部删除用户消息")
    @PostMapping("message/all_delete")
    public RestResp<Void> allDeleteUserMessages() {
        return messageService.allDeleteMessages(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_USER);
    }
}
