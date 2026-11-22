package com.nexus.world.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 事实创建请求，与 api-specification.md 5.2 节契约一致。
 * 抽取结果写入（B）时须传 valueType/sourceId/confidence/factStatus；人工录入均可缺省。
 */
public record FactCreateRequest(
        @NotNull(message = "subjectId 不能为空")
        Long subjectId,

        @NotBlank(message = "predicate 不能为空")
        @Size(max = 64, message = "predicate 长度须不超过 64")
        String predicate,

        @NotBlank(message = "objectValue 不能为空")
        String objectValue,

        @Pattern(regexp = "string|number|date|boolean", message = "valueType 取值非法")
        String valueType,

        @Size(max = 16, message = "unit 长度须不超过 16")
        String unit,

        String normalizedValue,

        String evidence,

        Long sourceId,

        Instant validFrom,

        // 手动登记证书类事实时用于承载到期时间（CERT_EXPIRY_30D 规则依赖）；抽取场景由 B 按 Schema 传入
        Instant validUntil,

        // P0 拍板（联调 2026-11-22）：抽取写入须携带置信度与事实状态，落库不再硬编码；
        // 人工录入可缺省，缺省时服务层按 1.0/confirmed 兜底（与 PR #1 拍板语义一致）
        @DecimalMin(value = "0.0", message = "confidence 须在 0-1 之间")
        @DecimalMax(value = "1.0", message = "confidence 须在 0-1 之间")
        BigDecimal confidence,

        @Pattern(regexp = "confirmed|inferred|unverified", message = "factStatus 取值非法")
        String factStatus) {
}