package com.nexus.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 任务更新请求：title/description/priority/dueDate 可选更新，status 走专用流转接口。
 */
public record TaskUpdateRequest(
        @Size(max = 255, message = "title 长度须不超过 255")
        String title,

        @Size(max = 4096, message = "description 长度须不超过 4096")
        String description,

        @Min(value = 0, message = "priority 取值 0-2")
        @Max(value = 2, message = "priority 取值 0-2")
        Integer priority,

        LocalDate dueDate) {
}