package com.nexus.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-6 拍板验证：风险落库后同步写入风险-证据事实关联表（world_risk_facts）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskRuleEngineTest {

    @Mock
    private WorldFactMapper factMapper;

    @Mock
    private WorldRiskMapper riskMapper;

    @Mock
    private WorldRiskFactMapper riskFactMapper;

    private RiskRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskRuleEngine(factMapper, riskMapper, riskFactMapper);
        doAnswer(invocation -> {
            invocation.getArgument(0, WorldRisk.class).setId(8001L);
            return 1;
        }).when(riskMapper).insert(any(WorldRisk.class));
        when(riskMapper.selectCount(any())).thenReturn(0L);
        when(riskFactMapper.insert(any(WorldRiskFact.class))).thenReturn(1);
    }

    private WorldFact certFactExpiringInDays(long days) {
        WorldFact fact = new WorldFact();
        fact.setId(300L);
        fact.setUserId(1L);
        fact.setSubjectId(100L);
        fact.setPredicate("has_certificate");
        fact.setObjectValue("PMP 证书");
        fact.setValidUntil(Instant.now().plusSeconds(java.time.Duration.ofDays(days).toSeconds()));
        fact.setCreatedAt(Instant.now());
        fact.setStatus("active");
        return fact;
    }

    @Test
    void certExpiryRiskWritesEvidenceFactLink() {
        when(factMapper.selectList(any())).thenReturn(List.of(certFactExpiringInDays(10)));

        engine.scanNow();

        ArgumentCaptor<WorldRisk> riskCaptor = ArgumentCaptor.forClass(WorldRisk.class);
        verify(riskMapper).insert(riskCaptor.capture());
        assertEquals("CERT_EXPIRY_30D", riskCaptor.getValue().getRuleCode());

        verify(riskFactMapper).insert(any(WorldRiskFact.class));
        ArgumentCaptor<WorldRiskFact> linkCaptor = ArgumentCaptor.forClass(WorldRiskFact.class);
        verify(riskFactMapper).insert(linkCaptor.capture());
        assertEquals(8001L, linkCaptor.getValue().getRiskId());
        assertEquals(300L, linkCaptor.getValue().getFactId());
    }

    @Test
    void existingRiskSkipsDuplicateAndLink() {
        when(factMapper.selectList(any())).thenReturn(List.of(certFactExpiringInDays(10)));
        when(riskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        engine.scanNow();

        verify(riskMapper, org.mockito.Mockito.never()).insert(any(WorldRisk.class));
        verify(riskFactMapper, org.mockito.Mockito.never()).insert(any(WorldRiskFact.class));
    }
}