package com.novel.user.controller.inner;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.service.AuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部调用-作家模块
 */
@Tag(name = "InnerAuthorController", description = "内部调用-作家模块")
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_AUTHOR_URL_PREFIX)
@RequiredArgsConstructor
public class InnerAuthorController {

    private final AuthorService authorService;

    /**
     * 扣除作者积分
     */
    @Operation(summary = "扣除作者积分")
    @PostMapping("points/deduct")
    public RestResp<Void> deductPoints(@RequestBody AuthorPointsConsumeReqDto dto) {
        return authorService.deductPoints(dto);
    }
}

