package com.nexus.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 任务状态流转请求。合法流转：todo → in_progress → done；todo/in_progress → cancelled。
 */
public record TaskStatusRequest(
        @NotBlank(message = "status 不能为空")
        @Pattern(regexp = "todo|in_progress|done|cancelled", message = "status 取值非法")
        String status) {
}