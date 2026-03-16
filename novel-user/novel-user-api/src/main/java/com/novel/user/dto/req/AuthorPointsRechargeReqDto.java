package com.novel.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者积分充值请求 DTO
 */
@Data
@Schema(description = "作者积分充值请求")
public class AuthorPointsRechargeReqDto {

    @Schema(description = "充值金额（单位：分，最小值为1分）")
    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额必须大于0")
    private Integer rechargeAmount;

    @Schema(description = "充值方式;0-支付宝 1-微信", example = "0")
    @NotNull(message = "充值方式不能为空")
    private Integer payChannel;
}
