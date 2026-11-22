package com.nexus.risk.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.RiskAnalysisProvider;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段四验证：RiskAnalysisProvider 接入为生产风险源，落库后写 world_risk_facts 证据关联（P1-6）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskAnalysisPipelineServiceTest {

    @Mock
    private WorldFactMapper factMapper;

    @Mock
    private WorldEntityMapper entityMapper;

    @Mock
    private WorldEventMapper eventMapper;

    @Mock
    private WorldRiskMapper riskMapper;

    @Mock
    private WorldRiskFactMapper riskFactMapper;

    @Mock
    private RiskAnalysisProvider riskAnalysisProvider;

    private RiskAnalysisPipelineService pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new RiskAnalysisPipelineService(factMapper, entityMapper, eventMapper,
                riskMapper, riskFactMapper, riskAnalysisProvider);
        doAnswer(invocation -> {
            invocation.getArgument(0, WorldRisk.class).setId(8001L);
            return 1;
        }).when(riskMapper).insert(any(WorldRisk.class));
        when(riskMapper.selectCount(any())).thenReturn(0L);
        when(riskFactMapper.insert(any(WorldRiskFact.class))).thenReturn(1);
    }

    private WorldFact activeFact() {
        WorldFact fact = new WorldFact();
        fact.setId(300L);
        fact.setUserId(1L);
        fact.setSubjectId(100L);
        fact.setPredicate("warranty_until");
        fact.setObjectValue("2026-12-01");
        fact.setConfidence(new BigDecimal("0.95"));
        fact.setStatus("active");
        return fact;
    }

    private WorldEntity subject() {
        WorldEntity entity = new WorldEntity();
        entity.setId(100L);
        entity.setUserId(1L);
        entity.setEntityType("asset");
        entity.setName("ThinkPad X1");
        entity.setStatus(1);
        return entity;
    }

    private void stubSources() {
        when(factMapper.selectList(any())).thenReturn(List.of(activeFact()));
        when(entityMapper.selectBatchIds(any())).thenReturn(List.of(subject()));
        when(eventMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void scanPersistsRiskWithEvidenceLinksAndSnapshot() {
        stubSources();
        when(riskAnalysisProvider.analyze(any())).thenReturn(new RiskAnalysisRequest.RiskAnalysisResult(
                List.of(new RiskAnalysisRequest.DetectedRisk("cert_expiry", "high", "ASSET_EXPIRY_30D",
                        "ThinkPad X1：保修 10 天内到期", "到期事实「warranty_until」=2026-12-01",
                        100L, List.of(300L)))));

        pipeline.scan();

        // 快照注入校验：实体 join 出 subjectName/subjectType，user_id 不来自抽取结果
        ArgumentCaptor<RiskAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(RiskAnalysisRequest.class);
        verify(riskAnalysisProvider).analyze(requestCaptor.capture());
        FactSnapshot snapshot = requestCaptor.getValue().facts().get(0);
        assertEquals("ThinkPad X1", snapshot.subjectName());
        assertEquals("asset", snapshot.subjectType());
        assertEquals(0.95, snapshot.confidence());
        assertEquals(1L, requestCaptor.getValue().analysisAt().getEpochSecond() > 0 ? 1L : 0L);

        ArgumentCaptor<WorldRisk> riskCaptor = ArgumentCaptor.forClass(WorldRisk.class);
        verify(riskMapper).insert(riskCaptor.capture());
        assertEquals("ASSET_EXPIRY_30D", riskCaptor.getValue().getRuleCode());
        assertEquals(1L, riskCaptor.getValue().getUserId());

        ArgumentCaptor<WorldRiskFact> linkCaptor = ArgumentCaptor.forClass(WorldRiskFact.class);
        verify(riskFactMapper).insert(linkCaptor.capture());
        assertEquals(8001L, linkCaptor.getValue().getRiskId());
        assertEquals(300L, linkCaptor.getValue().getFactId());
    }

    @Test
    void scanDropsRiskWithoutEntityId() {
        stubSources();
        when(riskAnalysisProvider.analyze(any())).thenReturn(new RiskAnalysisRequest.RiskAnalysisResult(
                List.of(new RiskAnalysisRequest.DetectedRisk("other", "medium", "SCHEDULE_CONFLICT",
                        "日程冲突", "同日两项安排", null, List.of()))));

        pipeline.scan();

        verify(riskMapper, never()).insert(any(WorldRisk.class));
        verify(riskFactMapper, never()).insert(any(WorldRiskFact.class));
    }

    @Test
    void scanSkipsResolvedDuplicateByRuleCode() {
        stubSources();
        when(riskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(riskAnalysisProvider.analyze(any())).thenReturn(new RiskAnalysisRequest.RiskAnalysisResult(
                List.of(new RiskAnalysisRequest.DetectedRisk("cert_expiry", "high", "ASSET_EXPIRY_30D",
                        "ThinkPad X1：保修 10 天内到期", "到期事实", 100L, List.of(300L)))));

        pipeline.scan();

        verify(riskMapper, never()).insert(any(WorldRisk.class));
        verify(riskFactMapper, never()).insert(any(WorldRiskFact.class));
    }

    @Test
    void scanSkipsEmptyFacts() {
        when(factMapper.selectList(any())).thenReturn(List.of());

        pipeline.scan();

        verify(riskAnalysisProvider, never()).analyze(any());
        verify(eventMapper, never()).selectList(any());
        assertNull(null);
    }
}