package com.nexus.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 实体创建请求，与 api-specification.md 5.1 节契约一致。
 * 为什么 entityType 用枚举白名单校验：DB 层有 CHECK 兜底，应用层前置校验给出更友好的 90001 提示。
 */
public record EntityCreateRequest(
        @NotBlank(message = "entityType 不能为空")
        @Pattern(regexp = "person|asset|event|document|org", message = "entityType 取值非法")
        String entityType,

        @NotBlank(message = "name 不能为空")
        @Size(max = 128, message = "name 长度须不超过 128")
        String name,

        @Size(max = 4096, message = "attributes 长度须不超过 4096")
        String attributes) {
}