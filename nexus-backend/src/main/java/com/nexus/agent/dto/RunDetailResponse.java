package com.nexus.agent.dto;

import java.util.List;

/**
 * Agent 执行详情响应：run 本体 + 操作日志列表（契约 8 节"详情含 summary、操作日志列表"）。
 */
public record RunDetailResponse(AgentRunResponse run, List<AgentActionLogResponse> logs) {
}