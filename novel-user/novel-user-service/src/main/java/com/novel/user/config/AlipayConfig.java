package com.novel.user.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置类
 */
@Configuration
@ConfigurationProperties(prefix = "alipay")
@Data
public class AlipayConfig {

    /**
     * 应用ID（APPID）
     */
    private String appId;

    /**
     * 应用私钥（应用私钥，用于签名）
     */
    private String appPrivateKey;

    /**
     * 支付宝公钥（支付宝公钥，用于验签）
     */
    private String alipayPublicKey;

    /**
     * 网关地址
     * 沙箱环境：https://openapi.alipaydev.com/gateway.do
     * 正式环境：https://openapi.alipay.com/gateway.do
     */
    private String gatewayUrl;

    /**
     * 签名算法类型（推荐使用RSA2）
     */
    private String signType = "RSA2";

    /**
     * 字符编码格式
     */
    private String charset = "UTF-8";

    /**
     * 返回格式
     */
    private String format = "JSON";

    /**
     * 异步通知地址（支付成功后支付宝回调的地址）
     */
    private String notifyUrl;

    /**
     * 同步跳转地址（支付完成后用户跳转的地址）
     */
    private String returnUrl;

    /**
     * 创建支付宝客户端
     */
    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                appPrivateKey,
                format,
                charset,
                alipayPublicKey,
                signType
        );
    }
}
