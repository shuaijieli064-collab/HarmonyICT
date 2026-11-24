package com.nexus.asset.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 资产汇总响应：按实体类型统计、价格类事实求和、未关闭风险数。
 * valueUnit 取价格类事实中出现最多的单位（众数），无单位事实时为 null。
 */
public record AssetSummaryResponse(long totalAssets, Map<String, Long> byType,
                                   BigDecimal totalValue, String valueUnit,
                                   long openRiskCount) {
}
