package com.nexus.ai.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.rules.AssetExpiryRule;
import com.nexus.ai.risk.rules.BudgetRule;
import com.nexus.ai.risk.rules.DeadlineRule;
import com.nexus.ai.risk.rules.ScheduleConflictRule;

class DefaultRiskAnalysisProviderTest {

    private static final Instant ANALYSIS_AT = Instant.parse("2026-11-21T00:00:00Z");

    private final DefaultRiskAnalysisProvider provider = new DefaultRiskAnalysisProvider(List.of(
            new DeadlineRule(), new AssetExpiryRule(),
            new ScheduleConflictRule(), new BudgetRule()));

    @Test
    void detectsTaskDeadlineWithinSevenDays() {
        FactSnapshot fact = fact(1L, 10L, "项目", "task_due_at", "2026-11-24", null);
        RiskAnalysisRequest request = new RiskAnalysisRequest(List.of(fact), List.of(), ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("contract_deadline", risk.riskType());
        assertEquals("mid", risk.level());
        assertEquals("DEADLINE_7D", risk.ruleCode());
        assertEquals(List.of(1L), risk.evidenceFactIds());
    }

    @Test
    void detectsWarrantyExpiryWithinSevenDays() {
        FactSnapshot fact = fact(2L, 20L, "笔记本", "warranty_until", "2026-11-23", null);
        RiskAnalysisRequest request = new RiskAnalysisRequest(List.of(fact), List.of(), ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("cert_expiry", risk.riskType());
        assertEquals("mid", risk.level());
        assertEquals("WARRANTY_EXPIRY_7D", risk.ruleCode());
    }

    @Test
    void detectsSubscriptionRenewalWithinThirtyDays() {
        FactSnapshot fact = fact(3L, 30L, "音乐会员", "subscription_renewal_at", "2026-12-15", null);
        RiskAnalysisRequest request = new RiskAnalysisRequest(List.of(fact), List.of(), ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("contract_deadline", risk.riskType());
        assertEquals("low", risk.level());
        assertEquals("SUBSCRIPTION_EXPIRY_30D", risk.ruleCode());
    }

    @Test
    void ignoresExpiredFact() {
        FactSnapshot fact = fact(4L, 40L, "笔记本", "warranty_until", "2026-11-20",
                Instant.parse("2026-11-20T00:00:00Z"));
        RiskAnalysisRequest request = new RiskAnalysisRequest(List.of(fact), List.of(), ANALYSIS_AT);

        assertTrue(provider.analyze(request).risks().isEmpty());
    }

    @Test
    void detectsScheduleConflictInSameHour() {
        List<EventSnapshot> events = List.of(
                event(1L, 50L, "项目评审", "2026-11-22T10:00:00Z"),
                event(2L, 50L, "客户会议", "2026-11-22T10:30:00Z"));
        RiskAnalysisRequest request = new RiskAnalysisRequest(List.of(), events, ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("other", risk.riskType());
        assertEquals("mid", risk.level());
        assertEquals("SCHEDULE_CONFLICT_HOUR", risk.ruleCode());
        assertTrue(risk.title().contains("2 项日程"));
    }

    @Test
    void warnsWhenSubscriptionSpendReachesEightyPercentOfBudget() {
        List<FactSnapshot> facts = List.of(
                fact(5L, 60L, "预算", "budget_limit", "1000", null),
                fact(6L, 61L, "音乐会员", "subscription_price", "900", null),
                fact(7L, 61L, "音乐会员", "subscription_cycle", "monthly", null));
        RiskAnalysisRequest request = new RiskAnalysisRequest(facts, List.of(), ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("other", risk.riskType());
        assertEquals("mid", risk.level());
        assertEquals("BUDGET_WARNING", risk.ruleCode());
        assertTrue(risk.evidenceFactIds().containsAll(List.of(5L, 6L)));
    }

    @Test
    void flagsOverspentBudgetAsHigh() {
        List<FactSnapshot> facts = List.of(
                fact(5L, 60L, "预算", "budget_limit", "1000", null),
                fact(6L, 61L, "音乐会员", "subscription_price", "1200", null));
        RiskAnalysisRequest request = new RiskAnalysisRequest(facts, List.of(), ANALYSIS_AT);

        RiskAnalysisRequest.DetectedRisk risk = provider.analyze(request).risks().get(0);

        assertEquals("high", risk.level());
        assertEquals("BUDGET_OVERSPENT", risk.ruleCode());
    }

    private FactSnapshot fact(Long id, Long subjectId, String subjectName,
            String predicate, String objectValue, Instant validUntil) {
        return new FactSnapshot(id, subjectId, "asset", subjectName, predicate,
                objectValue, 0.9, ANALYSIS_AT.minusSeconds(86400), validUntil);
    }

    private EventSnapshot event(Long id, Long entityId, String title, String occurredAt) {
        return new EventSnapshot(id, entityId, "other", title, null, Instant.parse(occurredAt));
    }
}