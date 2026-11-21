package com.nexus.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：无状态 JWT 模式。
 * 为什么禁用 Session：前后端分离 + 横向扩展，认证信息只走 Token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 契约规范 5：除注册登录与健康检查外全部必需认证
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // 未认证统一返回 401，与契约错误码 10105 对齐（默认入口点是 403）
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // 只在安全链内构造，避免过滤器被 Boot 重复注册到全局链（见过滤器类注释）
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}