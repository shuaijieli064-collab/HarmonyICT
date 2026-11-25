package com.nexus.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与解析。
 * 为什么自解析而非交给过滤器封装：登录、退出、过滤器三处都要读写 claim，收敛到单一组件避免格式漂移。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expireHours;

    public JwtTokenProvider(@Value("${nexus.jwt.secret}") String secret,
                            @Value("${nexus.jwt.expire-hours}") long expireHours) {
        // 显式校验密钥长度：HS256 要求 >=256bit（32 字节）。prod 未配 NEXUS_JWT_SECRET 时
        // 占位符注入会在构造前失败（fail-fast），此处兜住短密钥场景并给出可读错误。
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "NEXUS_JWT_SECRET 必须至少 32 字节（HS256 要求 256bit），请配置足够长的随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /** 生成携带 userId 的访问令牌 */
    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌；非法或过期抛 JwtException 子类，由调用方转 AUTH_TOKEN_INVALID。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}