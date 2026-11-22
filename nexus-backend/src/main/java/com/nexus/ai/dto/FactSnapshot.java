package com.nexus.ai.dto;

import java.time.Instant;

/**
 * 事实快照（world_facts 只读投影，A 侧查询后传入，B 不直接查库）。
 * 为什么用快照：保持 com.nexus.ai 包与业务实体解耦，B 无需依赖 MyBatis-Plus 实体与 Mapper。
 *
 * @param id          world_facts.id
 * @param subjectId   world_entities.id
 * @param subjectType 主体类型：person / asset / event / document / org
 * @param subjectName 主体名称
 * @param predicate   谓词（如 has_certificate / monthly_income）
 * @param objectValue 宾值
 * @param confidence  置信度 0-1
 * @param validFrom   生效时间
 * @param validUntil  失效时间（null 表示当前有效）
 */
public record FactSnapshot(Long id, Long subjectId, String subjectType, String subjectName,
                           String predicate, String objectValue, Double confidence,
                           Instant validFrom, Instant validUntil) {
}