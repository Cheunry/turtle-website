package com.novel.user.controller.inner;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.MessageSendReqDto;
import com.novel.user.service.MessageService;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "InnerBookController", description = "内部调用-用户模块")
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_USER_URL_PREFIX)
@RequiredArgsConstructor
public class InnerUserController {

    private final UserService userService;
    private final MessageService messageService;

    /**
     * 批量查询用户信息
     */
    @Operation(summary = "批量查询用户信息")
    @PostMapping("listUserInfoByIds")
    RestResp<List<UserInfoRespDto>> listUserInfoByIds(@RequestBody List<Long> userIds) {
        return userService.listUserInfoByIds(userIds);
    }

    /**
     * 发送消息
     */
    @Operation(summary = "发送消息")
    @PostMapping("sendMessage")
    RestResp<Void> sendMessage(@RequestBody MessageSendReqDto dto) {
        messageService.sendMessage(dto);
        return RestResp.ok();
    }
}
