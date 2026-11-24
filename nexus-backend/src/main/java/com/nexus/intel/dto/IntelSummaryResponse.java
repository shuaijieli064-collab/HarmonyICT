package com.nexus.intel.dto;

import java.util.Map;

/**
 * 情报中心汇总响应：未关闭风险按等级分布、近期事件、待办与 Agent 动作统计。
 */
public record IntelSummaryResponse(long openRiskCount, Map<String, Long> riskByLevel,
                                   long recentEventCount, long pendingTaskCount,
                                   long recentAgentRunCount) {
}
