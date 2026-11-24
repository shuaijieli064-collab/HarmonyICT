package com.nexus.dashboard.dto;

import com.nexus.risk.entity.WorldRisk;
import com.nexus.world.entity.WorldEvent;

import java.time.Instant;
import java.util.List;

/**
 * 驾驶舱聚合统计响应（体量扩充新增）。
 * 为什么一次聚合：原驾驶舱前端要并发调 6 个分页接口拼指标，
 * 聚合接口一次返回全部概览计数与摘要列表，减少请求数与前端拼装逻辑。
 */
public record DashboardSummaryResponse(
        long entityTotal,
        long factTotal,
        long eventTotal,
        long openRiskTotal,
        long taskTotal,
        long todoTaskTotal,
        long agentRunTotal,
        long unreadNotificationTotal,
        List<RiskLevelCount> riskByLevel,
        List<RecentEvent> recentEvents,
        List<TopRisk> topRisks) {

    /** 风险等级分布（仅统计未解决 open/acknowledged） */
    public record RiskLevelCount(String level, long count) {
    }

    /** 近期事件摘要 */
    public record RecentEvent(Long id, String title, String eventType, Instant occurredAt) {

        public static RecentEvent from(WorldEvent event) {
            return new RecentEvent(event.getId(), event.getTitle(), event.getEventType(), event.getOccurredAt());
        }
    }

    /** 待处理风险摘要（按等级排序取前 N） */
    public record TopRisk(Long id, String level, String title, String ruleCode, String status) {

        public static TopRisk from(WorldRisk risk) {
            return new TopRisk(risk.getId(), risk.getLevel(), risk.getTitle(), risk.getRuleCode(), risk.getStatus());
        }
    }
}
