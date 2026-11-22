package com.nexus.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 用户资料更新请求，与 api-specification.md 3 节契约一致。username 不可改（认证主体标识）。
 */
public record UserProfileUpdateRequest(
        @Size(max = 32, message = "昵称长度须不超过 32")
        String nickname,

        @Size(max = 128, message = "邮箱长度须不超过 128")
        String email) {
}