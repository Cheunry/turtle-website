package com.novel.user.service;

import com.novel.user.dto.req.AuthorPointsRechargeReqDto;
import com.novel.user.dto.resp.AuthorPointsRechargeRespDto;
import com.novel.common.resp.RestResp;

/**
 * 支付服务接口
 */
public interface PaymentService {

    /**
     * 创建充值订单（支付宝）
     * @param authorId 作者ID
     * @param dto 充值请求DTO
     * @return 支付响应（包含支付表单或支付URL）
     */
    RestResp<AuthorPointsRechargeRespDto> createRechargeOrder(Long authorId, AuthorPointsRechargeReqDto dto);

    /**
     * 处理支付宝异步通知
     * @param params 支付宝回调参数
     * @return 处理结果
     */
    String handleAlipayNotify(java.util.Map<String, String> params);

    /**
     * 处理支付宝同步返回
     * @param params 支付宝返回参数
     * @return 处理结果
     */
    String handleAlipayReturn(java.util.Map<String, String> params);
}
