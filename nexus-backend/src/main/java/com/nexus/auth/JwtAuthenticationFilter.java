package com.nexus.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Bearer Token，绑定 SecurityContext 与 UserContext。
 * 为什么绑定两处：SecurityContext 供安全框架鉴权，UserContext 供业务层取 user_id 做数据隔离。
 * 为什么不注册为 Spring 组件：@Component 会被 Boot 自动加入全局 Servlet 过滤器链，
 * 提前消耗 OncePerRequestFilter 的执行标记，导致 Security 链内的同名过滤器被跳过、认证失效。
 * 本过滤器只由 SecurityConfig 在安全链内构造。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = tokenProvider.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                UserContext.set(userId);
                log.info("JWT 认证成功: userId={}", userId);
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌无效时不设置认证信息，由授权规则返回 401；记录 debug 级别避免刷日志
                log.debug("JWT 解析失败: {}", e.getMessage());
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程池复用，必须清理避免用户上下文串号（数据隔离红线）
            UserContext.clear();
        }
    }
}