package com.nexus.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Agent 执行发起请求，与 api-specification.md 8 节契约一致。
 */
public record RunCreateRequest(
        @NotBlank(message = "intent 不能为空")
        @Size(max = 255, message = "intent 长度须不超过 255")
        String intent,

        @Pattern(regexp = "manual|scheduled|event", message = "triggerType 取值非法")
        String triggerType) {
}