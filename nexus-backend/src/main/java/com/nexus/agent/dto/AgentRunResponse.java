package com.nexus.agent.dto;

import com.nexus.agent.entity.AgentRun;

import java.time.Instant;

/**
 * Agent 执行记录响应，字段与 api-specification.md 8 节契约一致。
 */
public record AgentRunResponse(Long id, String triggerType, String intent, String status, String summary,
                               Instant startedAt, Instant finishedAt) {

    public static AgentRunResponse from(AgentRun run) {
        return new AgentRunResponse(run.getId(), run.getTriggerType(), run.getIntent(), run.getStatus(),
                run.getSummary(), run.getStartedAt(), run.getFinishedAt());
    }
}