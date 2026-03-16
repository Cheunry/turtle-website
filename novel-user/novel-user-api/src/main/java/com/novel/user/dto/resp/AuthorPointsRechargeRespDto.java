package com.novel.user.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 作者积分充值响应 DTO
 */
@Data
@Schema(description = "作者积分充值响应")
public class AuthorPointsRechargeRespDto {

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "支付表单HTML（支付宝PC网站支付）")
    private String payFormHtml;

    @Schema(description = "支付URL（支付宝手机网站支付）")
    private String payUrl;
}
