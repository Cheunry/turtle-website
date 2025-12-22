package com.novel.user.controller.author;

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

@Tag(name = "AuthorMessageController", description = "作家后台-消息模块")
@RestController
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/message")
@RequiredArgsConstructor
public class AuthorMessageController {

    private final UserMessageService userMessageService;

    @Operation(summary = "获取作家消息列表")
    @GetMapping("list")
    public RestResp<PageRespDto<MessageRespDto>> listMessages(@ParameterObject PageReqDto pageReqDto) {
        return userMessageService.listMessages(pageReqDto, true);
    }

    @Operation(summary = "获取作家未读消息数量")
    @GetMapping("unread_count")
    public RestResp<Long> getUnReadCount() {
        return userMessageService.getUnReadCount(true);
    }

    @Operation(summary = "阅读消息")
    @PostMapping("read/{id}")
    public RestResp<Void> readMessage(@Parameter(description = "消息ID") @PathVariable Long id) {
        return userMessageService.readMessage(id, true);
    }

}
