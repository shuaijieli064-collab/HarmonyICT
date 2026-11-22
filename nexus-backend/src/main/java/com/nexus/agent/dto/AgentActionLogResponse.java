package com.nexus.agent.dto;

import com.nexus.agent.entity.AgentActionLog;

import java.time.Instant;

/**
 * Agent 操作日志响应，字段与 api-specification.md 8 节契约一致。
 */
public record AgentActionLogResponse(Long id, String actionType, String level, String targetType,
                                     Long targetId, String payload, String result, Boolean authorized,
                                     Instant createdAt) {

    public static AgentActionLogResponse from(AgentActionLog log) {
        return new AgentActionLogResponse(log.getId(), log.getActionType(), log.getLevel(), log.getTargetType(),
                log.getTargetId(), log.getPayload(), log.getResult(), log.getAuthorized(), log.getCreatedAt());
    }
}