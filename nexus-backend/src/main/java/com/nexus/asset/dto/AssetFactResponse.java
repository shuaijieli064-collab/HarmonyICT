package com.nexus.asset.dto;

/**
 * 资产关联事实条目，来自 world_facts（active 状态）。
 * 展示资产的关键属性（购买日期 / 保修到期 / 价格 / 续费日期等）。
 */
public record AssetFactResponse(Long factId, String predicate, String objectValue, String valueType,
                                String unit, String normalizedValue, java.time.Instant validUntil) {
}
