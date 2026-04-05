package com.novel.user.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.user.config.AlipayConfig;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.AuthorPointsRechargeLog;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dao.mapper.AuthorPointsRechargeLogMapper;
import com.novel.user.dto.req.AuthorPointsRechargeReqDto;
import com.novel.user.dto.resp.AuthorPointsRechargeRespDto;
import com.novel.user.service.PaymentService;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 支付服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final AuthorPointsRechargeLogMapper rechargeLogMapper;
    private final AuthorInfoMapper authorInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 积分兑换比例：1元 = 100积分（1分 = 1积分）
     */
    private static final int POINTS_PER_YUAN = 100;

    @Override
    public RestResp<AuthorPointsRechargeRespDto> createRechargeOrder(Long authorId, AuthorPointsRechargeReqDto dto) {
        try {
            AuthorInfo authorInfo = authorInfoMapper.selectById(authorId);
            if (authorInfo == null) {
                return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
            }

            if (dto.getPayChannel() == null || dto.getPayChannel() != 0) {
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "暂不支持该支付方式");
            }

            String outTradeNo = generateOutTradeNo(authorId);
            Integer rechargeAmount = dto.getRechargeAmount();
            Integer rechargePoints = (rechargeAmount / 100) * POINTS_PER_YUAN;

            // 仅包裹写库，尽快提交释放连接；支付宝 HTTP 在事务外执行
            transactionTemplate.executeWithoutResult(status -> {
                AuthorPointsRechargeLog rechargeLog = new AuthorPointsRechargeLog();
                rechargeLog.setAuthorId(authorId);
                rechargeLog.setRechargeAmount(rechargeAmount);
                rechargeLog.setRechargePoints(rechargePoints);
                rechargeLog.setPayChannel(dto.getPayChannel());
                rechargeLog.setOutTradeNo(outTradeNo);
                rechargeLog.setRechargeStatus(0);
                rechargeLog.setCreateTime(LocalDateTime.now());
                rechargeLog.setUpdateTime(LocalDateTime.now());
                rechargeLogMapper.insert(rechargeLog);
            });

            String payUrl = createAlipayWapPayOrder(outTradeNo, rechargeAmount, "作者积分充值");

            AuthorPointsRechargeRespDto respDto = new AuthorPointsRechargeRespDto();
            respDto.setOutTradeNo(outTradeNo);
            respDto.setPayFormHtml(null);
            respDto.setPayUrl(payUrl);

            log.info("作者[{}]创建充值订单成功，订单号：{}，充值金额：{}分，获得积分：{}",
                    authorId, outTradeNo, rechargeAmount, rechargePoints);

            return RestResp.ok(respDto);

        } catch (AlipayApiException e) {
            log.error("创建充值订单失败（支付宝），作者ID：{}", authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "创建订单失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("创建充值订单失败，作者ID：{}", authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "创建订单失败：" + e.getMessage());
        }
    }

    /**
     * 创建支付宝手机网站支付订单
     */
    private String createAlipayWapPayOrder(String outTradeNo, Integer totalAmount, String subject) 
            throws AlipayApiException {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(outTradeNo);
        // 金额转换为元（支付宝接口要求单位为元）
        BigDecimal amount = new BigDecimal(totalAmount).divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);
        model.setTotalAmount(amount.toString());
        model.setSubject(subject);
        model.setProductCode("QUICK_WAP_WAY"); // 手机网站支付产品码
        
        request.setBizModel(model);
        request.setNotifyUrl(alipayConfig.getNotifyUrl()); // 异步通知地址
        request.setReturnUrl(alipayConfig.getReturnUrl()); // 同步跳转地址

        AlipayTradeWapPayResponse response = alipayClient.pageExecute(request);
        if (response.isSuccess()) {
            return response.getBody(); // 返回支付URL
        } else {
            throw new AlipayApiException("创建支付宝订单失败：" + response.getMsg());
        }
    }

    /**
     * 创建支付宝PC网站支付订单
     */
    private String createAlipayPagePayOrder(String outTradeNo, Integer totalAmount, String subject) 
            throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        // 金额转换为元
        BigDecimal amount = new BigDecimal(totalAmount).divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);
        model.setTotalAmount(amount.toString());
        model.setSubject(subject);
        model.setProductCode("FAST_INSTANT_TRADE_PAY"); // PC网站支付产品码
        
        request.setBizModel(model);
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
        if (response.isSuccess()) {
            return response.getBody(); // 返回支付表单HTML
        } else {
            throw new AlipayApiException("创建支付宝订单失败：" + response.getMsg());
        }
    }

    @Override
    public String handleAlipayNotify(Map<String, String> params) {
        try {
            // 1. 验签
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (!signVerified) {
                log.error("支付宝异步通知验签失败，参数：{}", params);
                return "failure";
            }

            // 2. 获取订单信息
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmount = params.get("total_amount");

            log.info("收到支付宝异步通知，订单号：{}，交易号：{}，交易状态：{}", outTradeNo, tradeNo, tradeStatus);

            // 3. 查询充值记录
            QueryWrapper<AuthorPointsRechargeLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("out_trade_no", outTradeNo);
            AuthorPointsRechargeLog rechargeLog = rechargeLogMapper.selectOne(queryWrapper);

            if (rechargeLog == null) {
                log.error("未找到充值记录，订单号：{}", outTradeNo);
                return "failure";
            }

            // 4. 处理支付结果
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 支付成功
                if (rechargeLog.getRechargeStatus() == 0) { // 只有待支付状态才处理
                    // 更新充值记录
                    rechargeLog.setTradeNo(tradeNo);
                    rechargeLog.setRechargeStatus(1); // 1-支付成功
                    rechargeLog.setRechargeTime(LocalDateTime.now());
                    rechargeLog.setUpdateTime(LocalDateTime.now());
                    rechargeLogMapper.updateById(rechargeLog);

                    // 增加作者积分
                    addAuthorPoints(rechargeLog.getAuthorId(), rechargeLog.getRechargePoints());

                    log.info("充值成功，订单号：{}，作者ID：{}，增加积分：{}", 
                            outTradeNo, rechargeLog.getAuthorId(), rechargeLog.getRechargePoints());
                }
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                // 交易关闭
                rechargeLog.setRechargeStatus(2); // 2-支付失败
                rechargeLog.setUpdateTime(LocalDateTime.now());
                rechargeLogMapper.updateById(rechargeLog);
                log.info("交易关闭，订单号：{}", outTradeNo);
            }

            return "success";

        } catch (Exception e) {
            log.error("处理支付宝异步通知失败", e);
            return "failure";
        }
    }

    @Override
    public String handleAlipayReturn(Map<String, String> params) {
        try {
            // 1. 验签
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (!signVerified) {
                log.error("支付宝同步返回验签失败");
                return "验签失败";
            }

            String outTradeNo = params.get("out_trade_no");
            log.info("支付宝同步返回，订单号：{}", outTradeNo);

            // 同步返回主要用于页面跳转，实际业务处理在异步通知中完成
            return "支付成功，订单号：" + outTradeNo;

        } catch (Exception e) {
            log.error("处理支付宝同步返回失败", e);
            return "处理失败";
        }
    }

    /**
     * 增加作者积分
     */
    private void addAuthorPoints(Long authorId, Integer points) {
        try {
            // 1. 更新数据库
            AuthorInfo authorInfo = authorInfoMapper.selectById(authorId);
            if (authorInfo != null) {
                Integer currentPaidPoints = authorInfo.getPaidPoints() != null ? authorInfo.getPaidPoints() : 0;
                authorInfo.setPaidPoints(currentPaidPoints + points);
                authorInfo.setUpdateTime(LocalDateTime.now());
                authorInfoMapper.updateById(authorInfo);
            }

            // 2. 更新Redis
            String paidPointsKey = String.format(CacheConsts.AUTHOR_PAID_POINTS_KEY, authorId);
            stringRedisTemplate.opsForValue().increment(paidPointsKey, points);

            log.info("作者[{}]积分增加成功，增加积分：{}", authorId, points);

        } catch (Exception e) {
            log.error("增加作者积分失败，作者ID：{}，积分：{}", authorId, points, e);
        }
    }

    /**
     * 生成商户订单号
     */
    private String generateOutTradeNo(Long authorId) {
        // 格式：RECHARGE + 作者ID + 时间戳 + 随机数
        return "RECHARGE" + authorId + System.currentTimeMillis() + 
               UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
