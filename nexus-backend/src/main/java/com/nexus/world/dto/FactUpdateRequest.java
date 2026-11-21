package com.nexus.world.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 事实更新请求：只更新宾值，更新走"链式失效"——旧事实置 superseded，新事实落库，保留历史可追溯。
 */
public record FactUpdateRequest(
        @NotBlank(message = "objectValue 不能为空")
        String objectValue) {
}