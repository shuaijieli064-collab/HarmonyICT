package com.nexus.graph.service.impl;

import com.nexus.graph.dto.WorldGraphResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 图谱聚合验证：四类边（fact_of / event_of / co_source / risk_on）、同源去重、孤儿事实剪枝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceImplTest {

    @Mock
    private WorldEntityMapper entityMapper;
    @Mock
    private WorldFactMapper factMapper;
    @Mock
    private WorldEventMapper eventMapper;
    @Mock
    private WorldRiskMapper riskMapper;

    private GraphServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GraphServiceImpl(entityMapper, factMapper, eventMapper, riskMapper);
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(riskMapper.selectList(any())).thenReturn(List.of());
    }

    private WorldEntity entity(long id, String name) {
        WorldEntity entity = new WorldEntity();
        entity.setId(id);
        entity.setUserId(1L);
        entity.setEntityType("asset");
        entity.setName(name);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private WorldFact fact(long id, long subjectId, Long sourceId) {
        WorldFact fact = new WorldFact();
        fact.setId(id);
        fact.setUserId(1L);
        fact.setSubjectId(subjectId);
        fact.setPredicate("subscription_price");
        fact.setObjectValue("39.00");
        fact.setSourceId(sourceId);
        fact.setConfidence(new BigDecimal("0.9"));
        fact.setStatus("active");
        fact.setCreatedAt(Instant.now());
        return fact;
    }

    @Test
    void sameSourceEntitiesAreLinkedWithCoSourceEdge() {
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(10L, "云空间"), entity(11L, "云空间旧号")));
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact(1L, 10L, 500L),
                fact(2L, 11L, 500L),
                fact(3L, 10L, 501L)));

        WorldGraphResponse graph = service.graph(1L);

        List<WorldGraphResponse.GraphLink> coSource = graph.links().stream()
                .filter(link -> "co_source".equals(link.kind())).toList();
        // 同源 500 的两实体建 1 条边；501 只有云空间一个实体，不建边
        assertEquals(1, coSource.size());
        assertTrue(coSource.get(0).label().contains("同源"));
    }

    @Test
    void orphanFactsArePrunedFromGraph() {
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(10L, "云空间")));
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact(1L, 10L, null),
                fact(9L, 999L, null))); // subject 不在实体集合内

        WorldGraphResponse graph = service.graph(1L);

        // 有效实体 1 + 有效事实 1 = 2 个节点；孤儿事实被剪枝
        assertEquals(2, graph.nodes().size());
        assertTrue(graph.nodes().stream()
                .noneMatch(node -> node.id().equals("fact:9")), "主体缺失的事实应被剪枝");
        assertEquals(1, graph.nodes().stream().filter(node -> "fact".equals(node.kind())).count());
    }

    @Test
    void eventAndRiskLinksAttachToOwnerEntity() {
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(10L, "笔记本")));
        when(factMapper.selectList(any())).thenReturn(List.of(fact(1L, 10L, null)));
        WorldEvent event = new WorldEvent();
        event.setId(300L);
        event.setUserId(1L);
        event.setEntityId(10L);
        event.setEventType("asset");
        event.setTitle("购买记录");
        event.setOccurredAt(Instant.parse("2026-11-20T08:00:00Z"));
        when(eventMapper.selectList(any())).thenReturn(List.of(event));
        WorldRisk risk = new WorldRisk();
        risk.setId(400L);
        risk.setUserId(1L);
        risk.setLevel("high");
        risk.setTitle("保修即将到期");
        risk.setRuleCode("WARRANTY_EXPIRY_30D");
        risk.setStatus("open");
        risk.setEntityId(10L);
        risk.setCreatedAt(Instant.now());
        when(riskMapper.selectList(any())).thenReturn(List.of(risk));

        WorldGraphResponse graph = service.graph(1L);

        assertTrue(graph.links().stream().anyMatch(link ->
                "event_of".equals(link.kind()) && link.target().equals("event:300")));
        assertTrue(graph.links().stream().anyMatch(link ->
                "risk_on".equals(link.kind()) && link.target().equals("risk:400")));
        assertTrue(graph.nodes().stream()
                .anyMatch(node -> "risk".equals(node.kind()) && node.meta().equals("WARRANTY_EXPIRY_30D")));
    }
}
