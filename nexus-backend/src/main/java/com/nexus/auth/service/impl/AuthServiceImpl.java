package com.nexus.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.auth.JwtTokenProvider;
import com.nexus.auth.dto.AuthResponse;
import com.nexus.auth.dto.LoginRequest;
import com.nexus.auth.dto.RegisterRequest;
import com.nexus.auth.service.AuthService;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.nexus.user.entity.User;
import com.nexus.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 认证服务实现。
 * 为什么密码用 BCrypt：慢哈希抗彩虹表/暴力破解，Spring Security 官方推荐。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String LOGOUT_KEY_PREFIX = "nexus:auth:logout:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.AUTH_USER_EXISTS);
        }

        Instant now = Instant.now();
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setEmail(request.email());
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        String token = tokenProvider.createToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        // 为什么统一报“用户名或密码错误”：避免通过错误信息区分“用户不存在/密码错误”被撞库探测
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ResultCode.AUTH_BAD_CREDENTIALS);
        }

        String token = tokenProvider.createToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname());
    }

    @Override
    public void logout(String token) {
        try {
            var claims = tokenProvider.parse(token);
            long ttlSeconds = Math.max(1,
                    (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
            redisTemplate.opsForValue().set(LOGOUT_KEY_PREFIX + token, "1", Duration.ofSeconds(ttlSeconds));
            log.info("令牌已加入退出黑名单: userId={}", claims.getSubject());
        } catch (Exception e) {
            // Redis 不可用或 token 已过期：退出属于低风险操作，降级不阻断
            log.warn("退出黑名单写入降级: {}", e.getMessage());
        }
    }
}