package com.novel.user.controller.front;

import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserBookshelfRespDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.dto.resp.UserRegisterRespDto;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.service.UserBookshelfService;
import com.novel.user.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "UserController", description = "前台门户-会员模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX)
@RequiredArgsConstructor
public class FrontUserController {

    private final UserInfoService userInfoService;
    private final UserBookshelfService userBookshelfService;
    private final BookFeignManager bookFeignManager;

    /* ***********************用户服务基础接口************************* */

    @Operation(summary = "用户注册接口")
    @PostMapping("register")
    public RestResp<UserRegisterRespDto> register(@Valid @RequestBody UserRegisterReqDto dto) {
        return userInfoService.register(dto);
    }

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public RestResp<UserLoginRespDto> login(@Valid @RequestBody UserLoginReqDto dto) {
        log.debug("用户登录请求: {}", dto);
        return userInfoService.login(dto);
    }

    @Operation(summary = "用户信息查询接口")
    @GetMapping
    public RestResp<UserInfoRespDto> getUserInfo() {
        return userInfoService.getUserInfo(UserHolder.getUserId());
    }

    @Operation(summary = "用户信息修改接口")
    @PutMapping
    public RestResp<Void> updateUserInfo(@Valid @RequestBody UserInfoUptReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return userInfoService.updateUserInfo(dto);
    }


    /* ***********************反馈相关接口************************* */

    /**
     * 用户反馈提交接口
     */
    @Operation(summary = "用户反馈提交接口")
    @PostMapping("feedback")
    public RestResp<Void> submitFeedback(@RequestBody String content) {
        return userInfoService.saveFeedback(UserHolder.getUserId(), content);
    }

    /**
     * 用户反馈删除接口
     */
    @Operation(summary = "用户反馈删除接口")
    @DeleteMapping("feedback/{id}")
    public RestResp<Void> deleteFeedback(@Parameter(description = "反馈ID") @PathVariable Long id) {
        return userInfoService.deleteFeedback(UserHolder.getUserId(), id);
    }


    /* ************************评论相关接口************************* */

    /**
     * 发表评论接口
     */
    @Operation(summary = "发表评论接口")
    @PostMapping("comment")
    public RestResp<Void> comment(@Valid @RequestBody BookCommentReqDto dto) {
        return bookFeignManager.publishComment(dto);
    }

    /**
     * 修改评论接口
     */
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

    /**
     * 删除评论接口
     */
    @Operation(summary = "删除评论接口")
    @DeleteMapping("comment/{id}")
    public RestResp<Void> deleteComment(@Parameter(description = "评论ID") @PathVariable Long id) {
        BookCommentReqDto dto = new BookCommentReqDto();
        dto.setUserId(UserHolder.getUserId());
        dto.setCommentId(id);
        return bookFeignManager.deleteComment(dto);
    }


    /* ************************书架相关接口************************* */

    /**
     * 查询书架状态接口 0-不在书架 1-已在书架
     */
    @Operation(summary = "查询书架状态接口")
    @GetMapping("bookshelf_status")
    public RestResp<Integer> getBookshelfStatus(@Parameter(description = "小说ID") String bookId) {
        return userBookshelfService.getBookshelfStatus(UserHolder.getUserId(), bookId);
    }

    @Operation(summary = "加入书架接口")
    @PostMapping("bookshelf")
    public RestResp<Void> addToBookshelf(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return userBookshelfService.addToBookshelf(UserHolder.getUserId(), bookId);
    }

    @Operation(summary = "查询书架列表接口")
    @GetMapping("bookshelf")
    public RestResp<List<UserBookshelfRespDto>> listBookshelf() {
        return userBookshelfService.listBookshelf(UserHolder.getUserId());
    }

    @Operation(summary = "更新书架阅读进度接口")
    @PutMapping("bookshelf/process")
    public RestResp<Void> updateBookshelfProcess(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId,
                                                 @Parameter(description = "章节号") @RequestParam("chapterNum") Integer chapterNum) {
        return userBookshelfService.updatePreChapterId(UserHolder.getUserId(), bookId, chapterNum);
    }

    @Operation(summary = "删除书架中的书籍")
    @DeleteMapping("bookshelf")
    public RestResp<Void> deleteBookshelf(@Parameter(description = "小说ID") @RequestParam("bookId") Long bookId) {
        return userBookshelfService.deleteBookshelf(UserHolder.getUserId(), bookId);
    }

}
