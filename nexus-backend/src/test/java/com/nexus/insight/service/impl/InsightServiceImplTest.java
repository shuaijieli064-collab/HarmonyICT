package com.nexus.insight.service.impl;

import com.nexus.insight.dto.InsightOverviewResponse;
import com.nexus.insight.dto.TimelineResponse;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
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

/** 洞察服务：引擎族分组与时间线投影的纯逻辑验证。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightServiceImplTest {

    @Mock
    private WorldRiskMapper riskMapper;
    @Mock
    private WorldFactMapper factMapper;
    @Mock
    private WorldEventMapper eventMapper;
    @Mock
    private WorldEntityMapper entityMapper;

    private InsightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsightServiceImpl(riskMapper, factMapper, eventMapper, entityMapper);
    }

    private WorldRisk risk(String ruleCode, String level, String status) {
        WorldRisk risk = new WorldRisk();
        risk.setId(1L);
        risk.setUserId(10L);
        risk.setRuleCode(ruleCode);
        risk.setLevel(level);
        risk.setStatus(status);
        risk.setTitle("风险-" + ruleCode);
        return risk;
    }

    @Test
    void overviewGroupsOpenRisksByEngineFamilyAndExcludesResolved() {
        // 模拟 mapper 返回：resolved 已由 SQL 条件 .ne(status, "resolved") 过滤，不会出现在结果中
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk("BUDGET_OVERSPENT", "high", "open"),
                risk("BUDGET_WARNING", "medium", "acknowledged"),
                risk("SUBSCRIPTION_EXPIRY_7D", "medium", "open"),
                risk("WARRANTY_EXPIRY_30D", "low", "open"),
                risk("CERT_EXPIRY_30D", "low", "open"),
                risk("DEADLINE_1D", "high", "open")));

        InsightOverviewResponse response = service.overview(10L);

        assertEquals(6, response.totalOpen());
        assertEquals(4, response.families().size());
        var finance = response.families().stream()
                .filter(f -> f.family().equals("FINANCE")).findFirst().orElseThrow();
        assertEquals(2, finance.total());
        assertEquals(1, finance.high());
        var device = response.families().stream()
                .filter(f -> f.family().equals("DEVICE")).findFirst().orElseThrow();
        assertEquals(2, device.total());
        assertEquals(2, device.low());
    }

    @Test
    void overviewReturnsEmptyFamiliesWhenNoOpenRisk() {
        when(riskMapper.selectList(any())).thenReturn(List.of());

        InsightOverviewResponse response = service.overview(10L);

        assertEquals(0, response.totalOpen());
        assertTrue(response.families().isEmpty());
    }

    @Test
    void timelineProjectsFactsAndEventsSortedByDate() {
        Instant now = Instant.now();
        WorldFact fact = new WorldFact();
        fact.setId(100L);
        fact.setUserId(10L);
        fact.setSubjectId(20L);
        fact.setPredicate("subscription_renewal_at");
        fact.setStatus("active");
        fact.setValidUntil(now.plus(5, ChronoUnit.DAYS));

        WorldEvent event = new WorldEvent();
        event.setId(200L);
        event.setUserId(10L);
        event.setEntityId(20L);
        event.setEventType("contract");
        event.setTitle("会员自动续费");
        event.setOccurredAt(now.plus(3, ChronoUnit.DAYS));

        when(factMapper.selectList(any())).thenReturn(List.of(fact));
        when(eventMapper.selectList(any())).thenReturn(List.of(event));
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(20L, "云盘会员")));

        TimelineResponse response = service.timeline(10L, 30);

        assertEquals(2, response.count());
        assertEquals(30, response.days());
        // 事件（3 天后）排在到期（5 天后）之前
        assertEquals("event", response.items().get(0).kind());
        assertEquals("会员自动续费", response.items().get(0).title());
        assertEquals("expiry", response.items().get(1).kind());
        assertEquals("medium", response.items().get(1).level());
        assertEquals("云盘会员 · 订阅续费日", response.items().get(1).title());
        assertEquals(20L, response.items().get(1).entityId());
        assertEquals("fact", response.items().get(1).refType());
    }

    @Test
    void timelineMarksDeadlinePredicatesWithHighLevelWithinOneDay() {
        Instant now = Instant.now();
        WorldFact fact = new WorldFact();
        fact.setId(101L);
        fact.setUserId(10L);
        fact.setSubjectId(21L);
        fact.setPredicate("task_due_at");
        fact.setStatus("active");
        fact.setValidUntil(now.plus(12, ChronoUnit.HOURS));

        when(factMapper.selectList(any())).thenReturn(List.of(fact));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(21L, "期末报告")));

        TimelineResponse response = service.timeline(10L, 30);

        assertEquals(1, response.count());
        assertEquals("deadline", response.items().get(0).kind());
        assertEquals("high", response.items().get(0).level());
    }

    private WorldEntity entity(Long id, String name) {
        WorldEntity entity = new WorldEntity();
        entity.setId(id);
        entity.setUserId(10L);
        entity.setName(name);
        entity.setEntityType("asset");
        entity.setStatus(1);
        return entity;
    }
}
