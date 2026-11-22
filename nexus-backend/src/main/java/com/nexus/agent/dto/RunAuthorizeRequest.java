package com.nexus.agent.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * L2 操作授权请求，与 api-specification.md 8 节契约一致。
 * approve=true：logIds 对应日志置 authorized=true（允许执行）；
 * approve=false：置 result=denied（拒绝执行）。
 */
public record RunAuthorizeRequest(
        @NotEmpty(message = "logIds 不能为空")
        List<Long> logIds,

        @NotNull(message = "approve 不能为空")
        Boolean approve) {
}