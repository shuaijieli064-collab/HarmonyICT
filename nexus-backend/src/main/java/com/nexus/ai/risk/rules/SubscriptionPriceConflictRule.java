package com.nexus.ai.risk.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;
import com.nexus.ai.risk.RiskRule;

/**
 * 订阅价格冲突规则：同一主体存在多个 active 的订阅价格事实且金额不一致。
 * 典型场景：跨来源抽取（新发票 vs 旧账单）后价格事实未正确链式失效，
 * 世界模型出现互相矛盾的价格——这类数据冲突会直接影响预算折算与风险判断，需提示用户核实。
 */
@Component
public class SubscriptionPriceConflictRule implements RiskRule {

    private static final String PRICE_PREDICATE = "subscription_price";

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        // subjectId -> 去重后的不同价格 -> 价格事实（取首个作为代表，证据收集全部）
        Map<Long, Map<String, List<FactSnapshot>>> bySubject = new LinkedHashMap<>();
        for (FactSnapshot fact : context.facts()) {
            if (!context.isActive(fact) || !PRICE_PREDICATE.equals(fact.predicate())) {
                continue;
            }
            bySubject.computeIfAbsent(fact.subjectId(), id -> new LinkedHashMap<>())
                    .computeIfAbsent(fact.objectValue(), value -> new ArrayList<>())
                    .add(fact);
        }

        List<RiskAnalysisRequest.DetectedRisk> risks = new ArrayList<>();
        for (Map.Entry<Long, Map<String, List<FactSnapshot>>> entry : bySubject.entrySet()) {
            Map<String, List<FactSnapshot>> values = entry.getValue();
            if (values.size() < 2) {
                continue;
            }
            List<Long> evidence = values.values().stream()
                    .flatMap(List::stream)
                    .map(FactSnapshot::id)
                    .filter(id -> id != null)
                    .toList();
            String subjectName = values.values().iterator().next().get(0).subjectName();
            risks.add(new RiskAnalysisRequest.DetectedRisk(
                    "fact_conflict", "medium", "SUBSCRIPTION_PRICE_CONFLICT",
                    subjectName + " 的订阅价格存在冲突",
                    "同一主体存在 " + values.size() + " 个不同的订阅价格事实（"
                            + String.join(" vs ", values.keySet())
                            + "），可能来自不同期账单。请核实当前实际价格并更新事实，"
                            + "否则预算折算与风险判断可能失真。",
                    entry.getKey(), evidence));
        }
        return risks;
    }
}
