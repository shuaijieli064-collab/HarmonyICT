package com.nexus.auth.service;

import com.nexus.auth.dto.AuthResponse;
import com.nexus.auth.dto.LoginRequest;
import com.nexus.auth.dto.RegisterRequest;

/**
 * 认证服务接口。注册 / 登录 / 退出闭环。
 */
public interface AuthService {

    /** 注册新用户，用户名重复抛 AUTH_USER_EXISTS */
    AuthResponse register(RegisterRequest request);

    /** 用户名密码登录，失败抛 AUTH_BAD_CREDENTIALS */
    AuthResponse login(LoginRequest request);

    /**
     * 退出：将当前 token 加入 Redis 黑名单。
     * Redis 不可用时降级为仅客户端删除（token 短有效期兜底），不阻断退出流程。
     */
    void logout(String token);
}