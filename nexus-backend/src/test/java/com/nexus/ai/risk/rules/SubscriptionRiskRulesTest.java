package com.nexus.ai.risk.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;

/**
 * 新增订阅类规则验证：重复订阅（同名主体多实体）与订阅价格冲突（同主体多价格事实）。
 */
class SubscriptionRiskRulesTest {

    private static final Instant ANALYSIS_AT = Instant.parse("2026-11-24T00:00:00Z");

    private final DuplicateSubscriptionRule duplicateRule = new DuplicateSubscriptionRule();
    private final SubscriptionPriceConflictRule conflictRule = new SubscriptionPriceConflictRule();

    private FactSnapshot fact(Long id, Long subjectId, String subjectName, String predicate, String objectValue) {
        return new FactSnapshot(id, subjectId, "asset", subjectName, predicate,
                objectValue, 0.9, ANALYSIS_AT.minusSeconds(86400), null);
    }

    private RiskEvaluationContext context(FactSnapshot... facts) {
        return new RiskEvaluationContext(List.of(facts), List.of(), ANALYSIS_AT);
    }

    @Test
    void sameSubscriptionOnTwoSubjectsTriggersDuplicateRisk() {
        RiskEvaluationContext ctx = context(
                fact(1L, 10L, "华为云空间", "subscription_price", "39.00"),
                fact(2L, 11L, "华为云空间", "subscription_price", "39.00"));

        List<RiskAnalysisRequest.DetectedRisk> risks = duplicateRule.evaluate(ctx);

        assertEquals(1, risks.size());
        RiskAnalysisRequest.DetectedRisk risk = risks.get(0);
        assertEquals("DUPLICATE_SUBSCRIPTION", risk.ruleCode());
        assertEquals("medium", risk.level());
        assertTrue(risk.title().contains("华为云空间"));
        assertTrue(risk.evidenceFactIds().containsAll(List.of(1L, 2L)));
    }

    @Test
    void sameSubscriptionSameSubjectDoesNotDuplicate() {
        RiskEvaluationContext ctx = context(
                fact(1L, 10L, "华为云空间", "subscription_price", "39.00"),
                fact(2L, 10L, "华为云空间", "subscription_price", "39.00"));

        assertTrue(duplicateRule.evaluate(ctx).isEmpty(), "同一主体多条事实属于价格冲突范畴，不属于重复订阅");
    }

    @Test
    void conflictingPricesOnSameSubjectTriggerConflictRisk() {
        RiskEvaluationContext ctx = context(
                fact(1L, 10L, "华为云空间", "subscription_price", "39.00"),
                fact(2L, 10L, "华为云空间", "subscription_price", "45.00"));

        List<RiskAnalysisRequest.DetectedRisk> risks = conflictRule.evaluate(ctx);

        assertEquals(1, risks.size());
        RiskAnalysisRequest.DetectedRisk risk = risks.get(0);
        assertEquals("SUBSCRIPTION_PRICE_CONFLICT", risk.ruleCode());
        assertEquals("medium", risk.level());
        assertEquals(10L, risk.entityId());
        assertTrue(risk.evidenceFactIds().containsAll(List.of(1L, 2L)));
    }

    @Test
    void identicalPriceFactsOnSameSubjectStaySilent() {
        RiskEvaluationContext ctx = context(
                fact(1L, 10L, "华为云空间", "subscription_price", "39.00"),
                fact(2L, 10L, "华为云空间", "subscription_price", "39.00"));

        assertTrue(conflictRule.evaluate(ctx).isEmpty(), "金额一致的多条事实不构成冲突");
    }

    @Test
    void supersededFactsAreIgnored() {
        FactSnapshot expired = new FactSnapshot(9L, 10L, "asset", "华为云空间", "subscription_price",
                "29.00", 0.9, ANALYSIS_AT.minusSeconds(86400 * 30), ANALYSIS_AT.minusSeconds(60));
        RiskEvaluationContext ctx = new RiskEvaluationContext(
                List.of(expired, fact(1L, 10L, "华为云空间", "subscription_price", "39.00")), List.of(), ANALYSIS_AT);

        assertTrue(conflictRule.evaluate(ctx).isEmpty(), "已失效事实不参与冲突判定");
    }
}
