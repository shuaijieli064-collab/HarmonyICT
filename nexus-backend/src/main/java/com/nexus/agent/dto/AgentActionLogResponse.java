package com.nexus.agent.dto;

import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.util.AgentAuditSupport;

import java.time.Instant;

/**
 * Agent 操作日志响应，字段与 api-specification.md 8 节契约一致。
 * audit 为 V3 附加字段（从 payload 的 audit 段解析），既有 9 个字段顺序与类型保持不变。
 */
public record AgentActionLogResponse(Long id, String actionType, String level, String targetType,
                                     Long targetId, String payload, String result, Boolean authorized,
                                     Instant createdAt, AgentAuditTrail audit) {

    public static AgentActionLogResponse from(AgentActionLog log) {
        return new AgentActionLogResponse(log.getId(), log.getActionType(), log.getLevel(), log.getTargetType(),
                log.getTargetId(), log.getPayload(), log.getResult(), log.getAuthorized(), log.getCreatedAt(),
                AgentAuditSupport.read(log.getPayload()));
    }
}