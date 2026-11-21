package com.nexus.world.dto;

import jakarta.validation.constraints.Size;

/**
 * 实体更新请求：只允许改 name 与 attributes，entityType 建立后不可变（避免事实归属漂移）。
 */
public record EntityUpdateRequest(
        @Size(max = 128, message = "name 长度须不超过 128")
        String name,

        @Size(max = 4096, message = "attributes 长度须不超过 4096")
        String attributes) {
}