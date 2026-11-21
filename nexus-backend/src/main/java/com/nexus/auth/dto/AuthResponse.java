package com.nexus.auth.dto;

/**
 * 认证响应：登录/注册成功后返回 token 与用户基础信息。
 * 为什么不放 passwordHash：响应字段与契约文档一致，敏感字段永不外泄。
 */
public record AuthResponse(String token, Long userId, String username, String nickname) {
}