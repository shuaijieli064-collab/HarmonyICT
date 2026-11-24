package com.nexus.insight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 假设推演请求（P1）：POST /api/v1/decision/simulate。
 * scenario：cancel=退订推演 / keep=保留推演；合法性在服务层校验。
 */
public record SimulationRequest(@NotNull Long entityId, @NotBlank String scenario) {
}
