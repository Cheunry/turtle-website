package com.novel.user.controller.front;

import com.novel.user.dto.req.AuthorPointsRechargeReqDto;
import com.novel.user.dto.resp.AuthorPointsRechargeRespDto;
import com.novel.user.service.PaymentService;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付控制器
 */
@Tag(name = "PaymentController", description = "支付模块")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建充值订单
     */
    @Operation(summary = "创建作者积分充值订单")
    @PostMapping("/recharge")
    public RestResp<AuthorPointsRechargeRespDto> createRechargeOrder(
            @Valid @RequestBody AuthorPointsRechargeReqDto dto) {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }

        return paymentService.createRechargeOrder(authorId, dto);
    }

    /**
     * 支付宝异步通知接口
     */
    @Operation(summary = "支付宝异步通知接口（内部使用）")
    @PostMapping("/alipay/notify")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        log.info("收到支付宝异步通知，参数：{}", params);
        return paymentService.handleAlipayNotify(params);
    }

    /**
     * 支付宝同步返回接口
     */
    @Operation(summary = "支付宝同步返回接口（内部使用）")
    @GetMapping("/alipay/return")
    public String alipayReturn(@RequestParam Map<String, String> params) {
        log.info("收到支付宝同步返回，参数：{}", params);
        return paymentService.handleAlipayReturn(params);
    }
}
