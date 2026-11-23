package com.nexus.ai.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.rules.AssetExpiryRule;
import com.nexus.ai.risk.rules.BudgetRule;
import com.nexus.ai.risk.rules.DeadlineRule;
import com.nexus.ai.risk.rules.ScheduleConflictRule;

/**
 * 演示数据风险覆盖矩阵：用 nexus-ai/testdata/demo 三份资料抽取后应落库的事实/事件快照，
 * 一次性验证期限、日程冲突、保修/订阅到期、预算约束 4 类风险均可触发。
 *
 * 为什么用 Snapshot 而不是端到端：events 入库契约尚未与 A 对齐（见 EVENTS-INGESTION-CONTRACT.md），
 * 当前生产链路只能落 facts；本测试锁定"规则层在正确输入下必然触发"，避免把入库缺口误判为规则缺陷。
 * 基准时间固定为演示日 2026-11-23T00:00:00Z，与生成资料的日期偏移一致。
 */
class RiskCoverageMatrixTest {

    private static final Instant ANALYSIS_AT = Instant.parse("2026-11-23T00:00:00Z");

    private final DefaultRiskAnalysisProvider provider = new DefaultRiskAnalysisProvider(List.of(
            new DeadlineRule(), new AssetExpiryRule(),
            new ScheduleConflictRule(), new BudgetRule()));

    @Test
    void demoAssetsTriggerAllFourRiskCategories() {
        List<FactSnapshot> facts = List.of(
                fact(101L, 1L, "asset", "联想拯救者 Y9000P", "warranty_until", "2026-12-01"),
                fact(102L, 2L, "asset", "华为云音乐会员", "subscription_renewal_at", "2026-11-25"),
                fact(103L, 2L, "asset", "华为云音乐会员", "subscription_price", "19.00"),
                fact(104L, 2L, "asset", "华为云音乐会员", "subscription_cycle", "monthly"),
                fact(105L, 3L, "asset", "华为云空间", "subscription_price", "39.00"),
                fact(106L, 3L, "asset", "华为云空间", "subscription_cycle", "monthly"),
                fact(107L, 4L, "person", "我的月度预算", "budget_limit", "50.00"),
                fact(108L, 5L, "event", "初赛作品提交", "task_due_at", "2026-11-24"));
        List<EventSnapshot> events = List.of(
                event(201L, 6L, "ICT 大赛课程", "2026-11-25T10:00:00Z"),
                event(202L, 6L, "小组项目评审", "2026-11-25T10:30:00Z"),
                event(203L, 7L, "算法训练", "2026-11-26T09:00:00Z"),
                event(204L, 7L, "英语角", "2026-11-26T15:00:00Z"));

        RiskAnalysisRequest.RiskAnalysisResult result = provider
                .analyze(new RiskAnalysisRequest(facts, events, ANALYSIS_AT));
        Set<String> ruleCodes = result.risks().stream()
                .map(RiskAnalysisRequest.DetectedRisk::ruleCode)
                .collect(Collectors.toSet());

        assertTrue(ruleCodes.contains("DEADLINE_1D"), "期限监控应触发 1 天档：" + ruleCodes);
        assertTrue(ruleCodes.contains("SCHEDULE_CONFLICT_HOUR"), "同小时日程冲突应触发：" + ruleCodes);
        assertTrue(ruleCodes.contains("SCHEDULE_CONFLICT_DAY"), "同日非重叠日程应触发：" + ruleCodes);
        assertTrue(ruleCodes.contains("SUBSCRIPTION_EXPIRY_7D"), "订阅到期应触发 7 天档：" + ruleCodes);
        assertTrue(ruleCodes.contains("WARRANTY_EXPIRY_30D"), "保修到期应触发 30 天档：" + ruleCodes);
        assertTrue(ruleCodes.contains("BUDGET_OVERSPENT"), "月度订阅支出超预算应触发：" + ruleCodes);

        RiskAnalysisRequest.DetectedRisk budget = result.risks().stream()
                .filter(risk -> "BUDGET_OVERSPENT".equals(risk.ruleCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("high", budget.level());
        assertTrue(budget.evidenceFactIds().containsAll(List.of(107L, 103L, 105L)));
        assertEquals("订阅支出占月度预算 116%", budget.title());
    }

    @Test
    void yearlySubscriptionIsAmortizedBeforeBudgetCheck() {
        List<FactSnapshot> facts = List.of(
                fact(301L, 10L, "person", "我的月度预算", "budget_limit", "100.00"),
                fact(302L, 11L, "asset", "年度云盘", "subscription_price", "600.00"),
                fact(303L, 11L, "asset", "年度云盘", "subscription_cycle", "yearly"));

        RiskAnalysisRequest.RiskAnalysisResult result = provider
                .analyze(new RiskAnalysisRequest(facts, List.of(), ANALYSIS_AT));

        assertTrue(result.risks().isEmpty(), "600 元/年折算 50 元/月，仅占 50%，不应触发预算风险");
    }

    @Test
    void budgetBelowEightyPercentStaysSilent() {
        List<FactSnapshot> facts = List.of(
                fact(401L, 20L, "person", "我的月度预算", "budget_limit", "100.00"),
                fact(402L, 21L, "asset", "云音乐", "subscription_price", "79.00"),
                fact(403L, 21L, "asset", "云音乐", "subscription_cycle", "monthly"));

        RiskAnalysisRequest.RiskAnalysisResult result = provider
                .analyze(new RiskAnalysisRequest(facts, List.of(), ANALYSIS_AT));

        assertTrue(result.risks().isEmpty(), "79% 未达 80% 阈值，不应产生预算风险");
    }

    @Test
    void missingBudgetFactDoesNotGuess() {
        List<FactSnapshot> facts = List.of(
                fact(501L, 30L, "asset", "云音乐", "subscription_price", "99.00"),
                fact(502L, 30L, "asset", "云音乐", "subscription_cycle", "monthly"));

        RiskAnalysisRequest.RiskAnalysisResult result = provider
                .analyze(new RiskAnalysisRequest(facts, List.of(), ANALYSIS_AT));

        assertTrue(result.risks().isEmpty(), "没有预算事实时不允许凭空判定超支");
    }

    private FactSnapshot fact(Long id, Long subjectId, String subjectType, String subjectName,
            String predicate, String objectValue) {
        return new FactSnapshot(id, subjectId, subjectType, subjectName, predicate,
                objectValue, 0.9, ANALYSIS_AT.minusSeconds(86400), null);
    }

    private EventSnapshot event(Long id, Long entityId, String title, String occurredAt) {
        return new EventSnapshot(id, entityId, "other", title, null, Instant.parse(occurredAt));
    }
}