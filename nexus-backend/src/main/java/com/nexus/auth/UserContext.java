package com.nexus.auth;

/**
 * 当前用户上下文（ThreadLocal）。
 * 规范 2.4 红线：业务查询的 user_id 一律从这里取，禁止信任前端传入的 user_id。
 * 为什么不用 SecurityContext 直接取：业务层不应耦合 Spring Security 类型，统一从这里拿 Long userId 更简洁。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long get() {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new IllegalStateException("当前线程未绑定用户上下文，请确认已通过认证过滤器");
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}