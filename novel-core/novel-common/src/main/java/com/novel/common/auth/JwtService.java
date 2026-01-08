package com.novel.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

/**
 * JWT 服务类
 * 重构自 JwtUtils，支持配置注入和过期时间
 */
@Slf4j
@Service
public class JwtService {

    @Value("${novel.jwt.secret:E66559580A1ADF48CDD928516062F12E}")
    private String secret;

    @Value("${novel.jwt.expiration:604800000}")
    private long expiration;

    /**
     * 定义系统标识头常量
     */
    private static final String HEADER_SYSTEM_KEY = "systemKeyHeader";

    /**
     * 根据用户ID生成JWT
     *
     * @param uid       用户ID
     * @param systemKey 系统标识
     * @return JWT
     */
    public String generateToken(Long uid, String systemKey) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .header()
                .add(HEADER_SYSTEM_KEY, systemKey)
                .and()
                .subject(uid.toString())
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * 解析JWT返回用户ID
     *
     * @param token     JWT
     * @param systemKey 系统标识
     * @return 用户ID，解析失败返回null
     */
    public Long parseToken(String token, String systemKey) {
        try {
            Jws<Claims> claimsJws = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token);

            // 判断该 JWT 是否属于指定系统
            if (Objects.equals(claimsJws.getHeader().get(HEADER_SYSTEM_KEY), systemKey)) {
                // 检查是否过期（JWT库会自动检查，但这里可以额外验证）
                Claims claims = claimsJws.getPayload();
                if (claims.getExpiration().before(new Date())) {
                    log.warn("JWT已过期: {}", token);
                    return null;
                }
                return Long.parseLong(claims.getSubject());
            }

        } catch (JwtException e) {
            log.warn("JWT解析失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT解析异常", e);
        }
        return null;
    }

    /**
     * 获取Token的过期时间
     *
     * @param token JWT
     * @return 过期时间，解析失败返回null
     */
    public Date getExpirationDate(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token);
            return claimsJws.getPayload().getExpiration();
        } catch (JwtException e) {
            log.warn("获取JWT过期时间失败: {}", e.getMessage());
            return null;
        }
    }

}
