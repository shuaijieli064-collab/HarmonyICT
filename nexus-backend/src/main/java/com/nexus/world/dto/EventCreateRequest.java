package com.nexus.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 事件创建请求，与 api-specification.md 5.3 节契约一致。
 */
public record EventCreateRequest(
        Long entityId,

        @NotBlank(message = "eventType 不能为空")
        @Pattern(regexp = "education|career|asset|health|contract|other", message = "eventType 取值非法")
        String eventType,

        @NotBlank(message = "title 不能为空")
        @Size(max = 255, message = "title 长度须不超过 255")
        String title,

        @Size(max = 4096, message = "description 长度须不超过 4096")
        String description,

        Instant occurredAt,

        Long sourceId) {
}