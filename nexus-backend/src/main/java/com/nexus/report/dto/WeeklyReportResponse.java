package com.nexus.report.dto;

import java.time.Instant;
import java.util.List;

/**
 * 个人周期报告响应（体量扩充第二波）。
 * 口径：所有计数均以「报告期内产生/完成」为准，全部限当前用户。
 */
public record WeeklyReportResponse(
        Instant periodStart,
        Instant periodEnd,
        int days,
        long newFactCount,
        long newEventCount,
        long newRiskCount,
        long taskCompletedCount,
        long agentRunCount,
        long notificationCount,
        List<RiskLevelCount> riskByLevel,
        List<DailyTrend> dailyTrend,
        List<PeriodRisk> notableRisks) {

    /** 报告期内新增风险按等级分布 */
    public record RiskLevelCount(String level, long count) {
    }

    /** 每日新增事实/事件趋势（世界模型活跃度） */
    public record DailyTrend(String day, long facts, long events) {
    }

    /** 报告期内代表性风险（按等级取前 3） */
    public record PeriodRisk(String id, String level, String title, String ruleCode) {
    }
}
