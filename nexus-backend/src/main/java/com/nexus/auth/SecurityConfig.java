package com.nexus.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 安全配置：无状态 JWT 模式。
 * 为什么禁用 Session：前后端分离 + 横向扩展，认证信息只走 Token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 本地/联调默认来源：前端 dev server 3000/3001；上云由 NEXUS_CORS_ORIGINS 覆盖 */
    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:3000", "http://localhost:3001");

    private final List<String> allowedOrigins;

    /** 为什么用构造器注入 + 手动 split：配置可能来自逗号分隔的环境变量，需去空格、去空项 */
    public SecurityConfig(@Value("${nexus.cors.origins:http://localhost:3000,http://localhost:3001}") String origins) {
        List<String> parsed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        this.allowedOrigins = parsed.isEmpty() ? DEFAULT_ORIGINS : parsed;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 端到端联调：C 的 http.ts 以完整 URL 直连后端（API_BASE_URL=localhost:8080），
                // 前端 dev server（3000）跨域，需放行预检与实际请求；生产同源部署后此配置可收紧
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

    /**
     * CORS 放行来源：默认本地 dev server（3000/3001），生产由 NEXUS_CORS_ORIGINS 注入公网 EIP/域名。
     * 为什么配置化：SecurityConfig 曾写死 localhost，上云后前端跨域必失败（见 cloud-deployment-plan 3.1/5.1）。
     * JWT 走 Authorization 头，不开 allowCredentials。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}