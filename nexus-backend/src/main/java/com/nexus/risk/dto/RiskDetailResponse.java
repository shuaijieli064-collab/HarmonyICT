package com.nexus.risk.dto;

import com.nexus.world.dto.FactResponse;

import java.util.List;

/**
 * 风险详情响应：风险本体 + 相关事实快照（契约 6 节"详情含相关事实快照"）。
 * 事实快照按 entity_id（风险关联实体）查询 active 事实，供 C 端展示风险依据。
 */
public record RiskDetailResponse(RiskResponse risk, List<FactResponse> relatedFacts) {
}