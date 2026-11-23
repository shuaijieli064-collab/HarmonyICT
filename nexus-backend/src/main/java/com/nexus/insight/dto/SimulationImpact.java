package com.nexus.insight.dto;

/**
 * 推演影响到的风险（假设口径）：真实风险引用其 ruleCode；
 * 推演新引入的假设风险以 SIM_KEEP_* 前缀标记，不落库。
 */
public record SimulationImpact(String riskType, String level, String title, String ruleCode) {
}
