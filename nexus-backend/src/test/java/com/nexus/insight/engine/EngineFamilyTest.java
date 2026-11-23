package com.nexus.insight.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 引擎族映射口径：与 ai/risk/rules 现有 ruleCode 前缀一一对应。 */
class EngineFamilyTest {

    @Test
    void budgetRuleCodesMapToFinanceFamily() {
        assertEquals(EngineFamily.FINANCE, EngineFamily.of("BUDGET_OVERSPENT"));
        assertEquals(EngineFamily.FINANCE, EngineFamily.of("BUDGET_WARNING"));
    }

    @Test
    void subscriptionRuleCodesMapToSubscriptionFamily() {
        assertEquals(EngineFamily.SUBSCRIPTION, EngineFamily.of("SUBSCRIPTION_EXPIRY_7D"));
        assertEquals(EngineFamily.SUBSCRIPTION, EngineFamily.of("SUBSCRIPTION_EXPIRY_30D"));
    }

    @Test
    void warrantyAndCertRuleCodesMapToDeviceFamily() {
        assertEquals(EngineFamily.DEVICE, EngineFamily.of("WARRANTY_EXPIRY_30D"));
        assertEquals(EngineFamily.DEVICE, EngineFamily.of("CERT_EXPIRY_30D"));
    }

    @Test
    void deadlineAndScheduleRuleCodesMapToLearningFamily() {
        assertEquals(EngineFamily.LEARNING, EngineFamily.of("DEADLINE_1D"));
        assertEquals(EngineFamily.LEARNING, EngineFamily.of("SCHEDULE_CONFLICT_HOUR"));
    }

    @Test
    void dataRuleCodesMapToDataQualityFamily() {
        assertEquals(EngineFamily.DATA_QUALITY, EngineFamily.of("DATA_STALENESS_180D"));
    }

    @Test
    void unknownOrNullRuleCodesFallBackToOther() {
        assertEquals(EngineFamily.OTHER, EngineFamily.of("SIM_KEEP_RENEWAL_30D"));
        assertEquals(EngineFamily.OTHER, EngineFamily.of(null));
        assertEquals(EngineFamily.OTHER, EngineFamily.of("  "));
    }
}
