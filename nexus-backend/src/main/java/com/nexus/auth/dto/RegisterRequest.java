package com.nexus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。字段命名小驼峰，与 api-specification.md 契约一致。
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度须为 3-32")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8-64")
        String password,

        @Size(max = 32, message = "昵称长度须不超过 32")
        String nickname,

        @Size(max = 128, message = "邮箱长度须不超过 128")
        String email) {
}