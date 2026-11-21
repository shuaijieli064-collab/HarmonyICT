package com.nexus.risk.dto;

import com.nexus.risk.entity.WorldRisk;

import java.time.Instant;

/**
 * 风险响应，字段与 api-specification.md 6 节契约一致。
 */
public record RiskResponse(Long id, String riskType, String level, String title, String description,
                           Long entityId, String ruleCode, String status, Instant createdAt, Instant updatedAt) {

    public static RiskResponse from(WorldRisk risk) {
        return new RiskResponse(risk.getId(), risk.getRiskType(), risk.getLevel(), risk.getTitle(),
                risk.getDescription(), risk.getEntityId(), risk.getRuleCode(), risk.getStatus(),
                risk.getCreatedAt(), risk.getUpdatedAt());
    }
}