package com.nexus.ai.risk.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;
import com.nexus.ai.risk.RiskRule;

/**
 * 重复订阅规则：同一个订阅服务名出现在多个主体（实体）上。
 * 典型场景：同一会员服务被登记为两个实体（换号/重复录入），造成重复扣费与预算虚高。
 * 只在同名主体的订阅价格事实≥2 个且分属不同 subjectId 时产生风险，避免误报。
 */
@Component
public class DuplicateSubscriptionRule implements RiskRule {

    private static final String PRICE_PREDICATE = "subscription_price";

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        // subjectName -> subjectId -> 该主体的订阅价格事实
        Map<String, Map<Long, FactSnapshot>> groups = new LinkedHashMap<>();
        for (FactSnapshot fact : context.facts()) {
            if (!context.isActive(fact) || !PRICE_PREDICATE.equals(fact.predicate())) {
                continue;
            }
            groups.computeIfAbsent(fact.subjectName(), name -> new LinkedHashMap<>())
                    .putIfAbsent(fact.subjectId(), fact);
        }

        List<RiskAnalysisRequest.DetectedRisk> risks = new ArrayList<>();
        for (Map.Entry<String, Map<Long, FactSnapshot>> group : groups.entrySet()) {
            Map<Long, FactSnapshot> bySubject = group.getValue();
            if (bySubject.size() < 2) {
                continue;
            }
            List<Long> evidence = bySubject.values().stream()
                    .map(FactSnapshot::id)
                    .filter(id -> id != null)
                    .toList();
            List<String> subjectNames = bySubject.keySet().stream().map(String::valueOf).toList();
            risks.add(new RiskAnalysisRequest.DetectedRisk(
                    "duplicate_subscription", "medium", "DUPLICATE_SUBSCRIPTION",
                    "疑似重复订阅：" + group.getKey(),
                    "订阅服务「" + group.getKey() + "」登记在 " + bySubject.size() + " 个主体上（主体 id："
                            + String.join("、", subjectNames) + "），疑似重复录入或换号残留，"
                            + "建议核对后合并实体，避免重复扣费。",
                    bySubject.keySet().iterator().next(), evidence));
        }
        return risks;
    }
}
