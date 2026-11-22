package com.nexus.world.dto;

import com.nexus.world.entity.WorldFact;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 事实响应，字段与 api-specification.md 5.2 节契约一致（PR #1 review 拍板补列）。
 */
public record FactResponse(Long id, Long subjectId, String predicate, String objectValue,
                           String valueType, String unit, String normalizedValue, String evidence,
                           Long sourceId, BigDecimal confidence, Instant validFrom, Instant validUntil,
                           String status, String factStatus, Instant createdAt, Instant updatedAt) {

    public static FactResponse from(WorldFact fact) {
        return new FactResponse(fact.getId(), fact.getSubjectId(), fact.getPredicate(), fact.getObjectValue(),
                fact.getValueType(), fact.getUnit(), fact.getNormalizedValue(), fact.getEvidence(),
                fact.getSourceId(), fact.getConfidence(), fact.getValidFrom(), fact.getValidUntil(),
                fact.getStatus(), fact.getFactStatus(), fact.getCreatedAt(), fact.getUpdatedAt());
    }
}