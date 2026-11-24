package com.nexus.insight.dto;

import java.math.BigDecimal;

/**
 * 推演逐月投影行：spend=当月支出（cancel 场景恒为 0），cumulative=累计值
 * （cancel 场景为累计节省，keep 场景为累计支出）。
 */
public record SimulationMonth(int month, String label, BigDecimal spend, BigDecimal cumulative) {
}
