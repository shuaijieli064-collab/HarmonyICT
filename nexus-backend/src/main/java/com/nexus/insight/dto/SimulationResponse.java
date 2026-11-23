package com.nexus.insight.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 假设推演响应（P1）。纯只读沙盘：不落库、不产生真实风险记录。
 */
public record SimulationResponse(Long entityId, String entityName, String scenario, BigDecimal monthlyCost,
                                 List<SimulationMonth> months, List<SimulationImpact> resolvedRisks,
                                 List<SimulationImpact> introducedRisks, String recommendation,
                                 List<String> rationale) {
}
