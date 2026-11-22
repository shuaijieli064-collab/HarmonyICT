package com.nexus.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 任务创建请求，与 api-specification.md 7 节契约一致。
 * sourceType 缺省 manual；riskId 关联来源风险（风险派生行动场景）。
 */
public record TaskCreateRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 255, message = "title 长度须不超过 255")
        String title,

        @Size(max = 4096, message = "description 长度须不超过 4096")
        String description,

        @Min(value = 0, message = "priority 取值 0-2")
        @Max(value = 2, message = "priority 取值 0-2")
        Integer priority,

        LocalDate dueDate,

        @Pattern(regexp = "manual|risk|agent", message = "sourceType 取值非法")
        String sourceType,

        Long riskId) {
}