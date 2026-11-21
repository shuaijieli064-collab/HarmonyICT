package com.nexus.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

        Instant validFrom) {
}