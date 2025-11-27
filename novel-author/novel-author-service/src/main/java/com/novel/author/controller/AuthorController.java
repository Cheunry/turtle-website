package com.novel.author.controller;

import com.novel.author.dto.req.AuthorRegisterReqDto;
import com.novel.author.manager.feign.BookFeignManager;
import com.novel.author.service.AuthorInfoService;
import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AuthorController", description = "作家后台-作者模块")
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
        authorInfoService.authorRegister(dto);
        return RestResp.ok();
    }


    /**
     *  作家登录接口
     */




    /**
     * 小说发布接口
     */
    @Operation(summary = "小说发布接口")
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {
        return bookFeignManager.publishBook(dto);
    }



    /**
     * 作家查看自己的书籍列表接口
     */
    @Operation(summary = "小说发布列表查询接口")
    @GetMapping("books")
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(@ParameterObject BookPageReqDto dto) {
        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeignManager.listPublishBooks(dto);
    }

    /**
     * 作家修改书籍接口
     */

    /**
     * 作家在网页端写书接口
     */


}
