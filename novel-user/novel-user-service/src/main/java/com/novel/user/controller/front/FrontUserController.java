package com.novel.user.controller.front;

import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.RestResp;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserController", description = "前台门户-会员模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX)
@RequiredArgsConstructor
public class FrontUserController {
    private final UserService userService;

    @Operation(summary = "用户注册接口")
    @PostMapping("register")
    public RestResp register(@Valid @RequestBody UserRegisterReqDto userRegisterReqDto) {
        return userService.register(userRegisterReqDto);
    }

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public RestResp<UserLoginRespDto> login(@Valid @RequestBody UserLoginReqDto userLoginReqDto) {
        System.out.println(userLoginReqDto);
        return userService.login(userLoginReqDto);
    }
}
