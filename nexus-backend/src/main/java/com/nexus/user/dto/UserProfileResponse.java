package com.nexus.user.dto;

import java.time.Instant;

/**
 * 当前用户资料响应。与 api-specification.md 3 节契约字段一致。
 */
public record UserProfileResponse(Long userId, String username, String nickname, String email, Instant createdAt) {
}