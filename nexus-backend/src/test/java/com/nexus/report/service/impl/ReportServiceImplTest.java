package com.nexus.report.service.impl;

import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.notification.entity.Notification;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.report.dto.WeeklyReportResponse;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 周报聚合验证：期内计数口径、每日趋势分桶、等级分布与代表性风险排序。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock
    private WorldFactMapper factMapper;
    @Mock
    private WorldEventMapper eventMapper;
    @Mock
    private WorldRiskMapper riskMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private NotificationMapper notificationMapper;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(factMapper, eventMapper, riskMapper,
                taskMapper, agentRunMapper, notificationMapper);
        when(taskMapper.selectCount(any())).thenReturn(3L);
        when(agentRunMapper.selectCount(any())).thenReturn(2L);
        when(notificationMapper.selectCount(any())).thenReturn(5L);
    }

    @Test
    void weeklyAggregatesPeriodCountsAndTrend() {
        Instant now = Instant.now();
        // 不做 UTC 截断：直接用当前时刻（必属今天）与 25h 前（必属昨天或前天，但都在报告期内）
        WorldFact factToday = fact(now);
        WorldFact factYesterday = fact(now.minus(90000, ChronoUnit.SECONDS));
        when(factMapper.selectList(any())).thenReturn(List.of(factToday, factYesterday));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(riskMapper.selectList(any())).thenReturn(List.of(risk("high")));

        WeeklyReportResponse report = service.weekly(1L, 7);

        assertEquals(7, report.days());
        assertEquals(2, report.newFactCount());
        assertEquals(1, report.newRiskCount());
        assertEquals(3L, report.taskCompletedCount());
        assertEquals(2L, report.agentRunCount());
        assertEquals(5L, report.notificationCount());
        assertEquals(7, report.dailyTrend().size(), "报告期每天一格");
        assertEquals(1L, report.dailyTrend().get(6).facts(), "最后一天应含今天的事实");
        assertEquals(1L, report.riskByLevel().stream()
                .filter(item -> "high".equals(item.level())).findFirst().orElseThrow().count());
    }

    @Test
    void daysOutOfBoundsFallBackToDefaults() {
        WeeklyReportResponse tooMany = service.weekly(1L, 999);
        assertEquals(31, tooMany.days(), "超过上限按 31 天截断");
        WeeklyReportResponse nonPositive = service.weekly(1L, 0);
        assertEquals(7, nonPositive.days(), "非正数回退默认 7 天");
    }

    @Test
    void notableRisksSortByLevelAndExcludesResolved() {
        Instant now = Instant.now();
        when(riskMapper.selectList(any())).thenReturn(List.of(
                riskWith("low", "open", "低风险", now),
                riskWith("high", "open", "高风险-早", now.minusSeconds(3600)),
                riskWith("high", "resolved", "已解决", now)));

        WeeklyReportResponse report = service.weekly(1L, 7);

        assertEquals(2, report.notableRisks().size(), "resolved 不进入代表性风险");
        assertEquals("high", report.notableRisks().get(0).level());
        assertTrue(report.notableRisks().get(0).title().contains("早"), "同等级新者在前");
    }

    private WorldFact fact(Instant createdAt) {
        WorldFact fact = new WorldFact();
        fact.setId(1L);
        fact.setUserId(1L);
        fact.setSubjectId(10L);
        fact.setPredicate("subscription_price");
        fact.setObjectValue("39.00");
        fact.setConfidence(new BigDecimal("0.9"));
        fact.setStatus("active");
        fact.setCreatedAt(createdAt);
        return fact;
    }

    private WorldRisk risk(String level) {
        WorldRisk risk = new WorldRisk();
        risk.setId(100L);
        risk.setUserId(1L);
        risk.setLevel(level);
        risk.setStatus("open");
        risk.setTitle("风险");
        risk.setRuleCode("RULE_X");
        risk.setCreatedAt(Instant.now());
        return risk;
    }

    private WorldRisk riskWith(String level, String status, String title, Instant createdAt) {
        WorldRisk risk = risk(level);
        risk.setStatus(status);
        risk.setTitle(title);
        risk.setCreatedAt(createdAt);
        return risk;
    }
}
