package com.novel.user.controller.front;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.user.service.UserMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FrontMessageController", description = "前台门户-消息模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/message")
@RequiredArgsConstructor
public class FrontMessageController {

    private final UserMessageService userMessageService;

    @Operation(summary = "获取用户消息列表")
    @GetMapping("list")
    public RestResp<PageRespDto<MessageRespDto>> listMessages(@ParameterObject PageReqDto pageReqDto) {
        return userMessageService.listMessages(pageReqDto, false);
    }

    @Operation(summary = "获取用户未读消息数量")
    @GetMapping("unread_count")
    public RestResp<Long> getUnReadCount() {
        return userMessageService.getUnReadCount(false);
    }

    @Operation(summary = "阅读消息")
    @PostMapping("read/{id}")
    public RestResp<Void> readMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return userMessageService.readMessage(id, false);
    }

}
