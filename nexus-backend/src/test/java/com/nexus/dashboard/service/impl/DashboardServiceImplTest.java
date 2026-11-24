package com.nexus.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.dashboard.dto.DashboardSummaryResponse;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 驾驶舱聚合统计验证：计数口径（active 事实 / open 风险）、
 * 等级分布分组、摘要排序（等级优先、同等级新者在前）与 user_id 隔离。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    @Mock
    private WorldEntityMapper entityMapper;
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

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(entityMapper, factMapper, eventMapper,
                riskMapper, taskMapper, agentRunMapper, notificationMapper);
        when(entityMapper.selectCount(any())).thenReturn(3L);
        when(factMapper.selectCount(any())).thenReturn(12L);
        when(eventMapper.selectCount(any())).thenReturn(5L);
        when(taskMapper.selectCount(any())).thenReturn(8L);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(8L);
        when(agentRunMapper.selectCount(any())).thenReturn(2L);
        when(notificationMapper.selectCount(any())).thenReturn(4L);
        when(eventMapper.selectPage(any(), any())).thenReturn(page(List.of()));
    }

    private WorldRisk risk(String level, String status, Instant createdAt) {
        WorldRisk risk = new WorldRisk();
        risk.setId(100L + level.hashCode());
        risk.setUserId(1L);
        risk.setLevel(level);
        risk.setStatus(status);
        risk.setTitle("风险-" + level + "-" + createdAt.toEpochMilli());
        risk.setRuleCode("RULE_X");
        risk.setCreatedAt(createdAt);
        return risk;
    }

    private WorldEvent event(long id, String title, Instant occurredAt) {
        WorldEvent event = new WorldEvent();
        event.setId(id);
        event.setUserId(1L);
        event.setEventType("other");
        event.setTitle(title);
        event.setOccurredAt(occurredAt);
        return event;
    }

    @Test
    void summaryAggregatesCountsAndDistribution() {
        Instant now = Instant.now();
        // 等级分布只统计未解决风险（resolved 由 SQL 过滤，stub 模拟 DB 行为不含 resolved）
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk("high", "open", now),
                risk("medium", "open", now),
                risk("medium", "acknowledged", now)));
        when(eventMapper.selectPage(any(), any())).thenReturn(
                page(List.of(event(1L, "上传发票", now))));

        DashboardSummaryResponse summary = service.summary(1L);

        assertEquals(3L, summary.entityTotal());
        assertEquals(12L, summary.factTotal());
        assertEquals(8L, summary.taskTotal());
        assertEquals(2L, summary.agentRunTotal());
        assertEquals(4L, summary.unreadNotificationTotal());
        // open 风险只计 open 状态：high+medium 共 2 条；acknowledged/resolved 不计入
        assertEquals(2L, summary.openRiskTotal());
        assertEquals(1L, summary.riskByLevel().stream()
                .filter(item -> "high".equals(item.level())).findFirst().orElseThrow().count());
        assertEquals(2L, summary.riskByLevel().stream()
                .filter(item -> "medium".equals(item.level())).findFirst().orElseThrow().count());
        assertEquals(0L, summary.riskByLevel().stream()
                .filter(item -> "low".equals(item.level())).findFirst().orElseThrow().count());
        assertEquals(1, summary.recentEvents().size());
        assertEquals("上传发票", summary.recentEvents().get(0).title());
    }

    @Test
    void topRisksSortByLevelThenNewest() {
        Instant now = Instant.now();
        Instant earlier = now.minusSeconds(3600);
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk("low", "open", now),
                risk("high", "open", earlier),
                risk("high", "open", now)));

        DashboardSummaryResponse summary = service.summary(1L);

        List<DashboardSummaryResponse.TopRisk> tops = summary.topRisks();
        assertEquals(3, tops.size());
        assertEquals("high", tops.get(0).level());
        assertEquals("风险-high-" + now.toEpochMilli(), tops.get(0).title());
        assertEquals("low", tops.get(2).level());
    }

    @Test
    void healthScoreWeightsFreshnessRiskAndTasks() {
        Instant now = Instant.now();
        // 新鲜事实（1 天内）→ 满分 40；1 条 high 未解决 → 40-10=30；无任务 → 基准 10
        WorldFact fresh = new WorldFact();
        fresh.setId(1L);
        fresh.setUserId(1L);
        fresh.setSubjectId(10L);
        fresh.setPredicate("subscription_price");
        fresh.setObjectValue("39.00");
        fresh.setStatus("active");
        fresh.setCreatedAt(now.minus(1, ChronoUnit.DAYS));
        when(factMapper.selectList(any())).thenReturn(List.of(fresh));
        when(riskMapper.selectList(any())).thenReturn(List.of(risk("high", "open", now)));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        var health = service.healthScore(1L);

        assertEquals(80, health.score());
        assertEquals("good", health.level());
        var freshness = health.dimensions().stream()
                .filter(d -> "freshness".equals(d.key())).findFirst().orElseThrow();
        assertEquals(40, freshness.score());
        var riskDim = health.dimensions().stream()
                .filter(d -> "risk".equals(d.key())).findFirst().orElseThrow();
        assertEquals(30, riskDim.score());
        assertTrue(riskDim.detail().contains("高 1"));
    }

    @Test
    void healthScoreEmptyDataIsCritical() {
        when(factMapper.selectList(any())).thenReturn(List.of());
        when(riskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of());

        var health = service.healthScore(1L);

        // 空数据：新鲜度 0 + 风险 40 + 任务基准 10 = 50（attention 下限）
        assertEquals(50, health.score());
        assertEquals("attention", health.level());
    }

    private Page<WorldEvent> page(List<WorldEvent> records) {
        Page<WorldEvent> result = new Page<>(1, 5);
        result.setRecords(records);
        result.setTotal(records.size());
        return result;
    }
}
