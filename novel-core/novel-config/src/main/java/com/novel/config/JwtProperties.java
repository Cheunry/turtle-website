package com.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置属性类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "novel.jwt")
public class JwtProperties {

    /**
     * JWT 加密密钥
     */
    private String secret = "E66559580A1ADF48CDD928516062F12E";

    /**
     * Token 过期时间（毫秒），默认7天
     */
    private long expiration = 7 * 24 * 60 * 60 * 1000L;

}
