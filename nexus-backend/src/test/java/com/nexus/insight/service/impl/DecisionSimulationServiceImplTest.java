package com.nexus.insight.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.insight.dto.SimulationRequest;
import com.nexus.insight.dto.SimulationResponse;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 假设推演：cancel/keep 两个场景的投影口径与守卫条件验证。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionSimulationServiceImplTest {

    @Mock
    private WorldEntityMapper entityMapper;
    @Mock
    private WorldFactMapper factMapper;
    @Mock
    private WorldRiskMapper riskMapper;

    private DecisionSimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DecisionSimulationServiceImpl(entityMapper, factMapper, riskMapper);
    }

    private WorldEntity entity(Long id) {
        WorldEntity entity = new WorldEntity();
        entity.setId(id);
        entity.setUserId(10L);
        entity.setName("云盘会员（按月自动续费）");
        entity.setEntityType("asset");
        entity.setStatus(1);
        return entity;
    }

    private WorldFact fact(String predicate, String normalizedValue, String unit) {
        WorldFact fact = new WorldFact();
        fact.setId(100L);
        fact.setUserId(10L);
        fact.setSubjectId(20L);
        fact.setPredicate(predicate);
        fact.setNormalizedValue(normalizedValue);
        fact.setUnit(unit);
        fact.setStatus("active");
        return fact;
    }

    private WorldRisk risk(Long entityId, String ruleCode, String level) {
        WorldRisk risk = new WorldRisk();
        risk.setId(900L);
        risk.setUserId(10L);
        risk.setEntityId(entityId);
        risk.setRuleCode(ruleCode);
        risk.setLevel(level);
        risk.setStatus("open");
        risk.setTitle("风险-" + ruleCode);
        return risk;
    }

    private void stubEntityWithSubscriptionFacts(Long entityId) {
        when(entityMapper.selectById(entityId)).thenReturn(entity(entityId));
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact("subscription_price", "30.00", "CNY"),
                fact("subscription_cycle", "monthly", null),
                fact("auto_renew", "true", null),
                fact("subscription_renewal_at", "2026-12-05", null)));
    }

    @Test
    void cancelScenarioProjectsTwelveMonthsSavingAndResolvesOpenRisks() {
        stubEntityWithSubscriptionFacts(20L);
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk(20L, "SUBSCRIPTION_EXPIRY_7D", "medium"),
                risk(20L, "BUDGET_WARNING", "low")));

        SimulationResponse response = service.simulate(10L,
                new SimulationRequest(20L, "cancel"));

        assertEquals("cancel", response.scenario());
        assertEquals(0, new BigDecimal("30.00").compareTo(response.monthlyCost()));
        assertEquals(12, response.months().size());
        assertEquals(0, new BigDecimal("360.00").compareTo(response.months().get(11).cumulative()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.months().get(11).spend()));
        assertEquals(2, response.resolvedRisks().size());
        assertEquals("SUBSCRIPTION_EXPIRY_7D", response.resolvedRisks().get(0).ruleCode());
        assertTrue(response.introducedRisks().isEmpty());
        assertTrue(response.recommendation().contains("360"));
    }

    @Test
    void keepScenarioIntroducesRenewalRiskWithinThirtyDays() {
        stubEntityWithSubscriptionFacts(20L);

        SimulationResponse response = service.simulate(10L,
                new SimulationRequest(20L, "keep"));

        assertEquals("keep", response.scenario());
        assertEquals(12, response.months().size());
        assertEquals(0, new BigDecimal("30.00").compareTo(response.months().get(5).spend()));
        assertTrue(response.resolvedRisks().isEmpty());
        assertEquals(1, response.introducedRisks().size());
        var impact = response.introducedRisks().get(0);
        assertEquals("SIM_KEEP_RENEWAL_30D", impact.ruleCode());
        assertEquals("contract", impact.riskType());
        assertTrue(response.recommendation().contains("假设性风险"));
    }

    @Test
    void keepScenarioIntroducesBudgetOverRiskWhenPriceExceedsBudgetLimit() {
        when(entityMapper.selectById(20L)).thenReturn(entity(20L));
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact("subscription_price", "80.00", "CNY"),
                fact("budget_limit", "50", "CNY"),
                fact("auto_renew", "false", null)));

        SimulationResponse response = service.simulate(10L,
                new SimulationRequest(20L, "keep"));

        assertEquals(1, response.introducedRisks().size());
        assertEquals("SIM_KEEP_BUDGET_OVER", response.introducedRisks().get(0).ruleCode());
        assertEquals("high", response.introducedRisks().get(0).level());
    }

    @Test
    void missingPriceFactIsRejectedWithParamInvalid() {
        when(entityMapper.selectById(20L)).thenReturn(entity(20L));
        when(factMapper.selectList(any())).thenReturn(List.of(fact("auto_renew", "true", null)));

        BizException exception = assertThrows(BizException.class,
                () -> service.simulate(10L, new SimulationRequest(20L, "cancel")));

        assertTrue(exception.getMessage().contains("subscription_price"));
    }

    @Test
    void entityBelongingToAnotherUserIsRejected() {
        when(entityMapper.selectById(20L)).thenReturn(entity(20L));

        assertThrows(BizException.class,
                () -> service.simulate(99L, new SimulationRequest(20L, "cancel")));
    }

    @Test
    void unknownScenarioIsRejected() {
        assertThrows(BizException.class,
                () -> service.simulate(10L, new SimulationRequest(20L, "pause")));
    }

    @Test
    void nonRenewalWithinThirtyDaysIntroducesNoRisk() {
        when(entityMapper.selectById(20L)).thenReturn(entity(20L));
        WorldFact farRenewal = fact("subscription_renewal_at",
                Instant.now().plus(60, ChronoUnit.DAYS).toString(), null);
        when(factMapper.selectList(any())).thenReturn(List.of(
                fact("subscription_price", "30.00", "CNY"),
                fact("auto_renew", "true", null),
                farRenewal));

        SimulationResponse response = service.simulate(10L,
                new SimulationRequest(20L, "keep"));

        assertTrue(response.introducedRisks().isEmpty());
        assertTrue(response.recommendation().contains("可保留"));
    }
}
