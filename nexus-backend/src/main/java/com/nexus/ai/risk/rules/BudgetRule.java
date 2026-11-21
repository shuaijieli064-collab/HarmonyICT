package com.nexus.ai.risk.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;
import com.nexus.ai.risk.RiskRule;

/**
 * 预算约束规则：把订阅价格按周期折算为月度支出，与月度预算比较。
 * 只在用户确实录入预算事实时判断，没有预算数据不产生风险，避免凭空推测。
 */
@Component
public class BudgetRule implements RiskRule {

    private static final Set<String> BUDGET_PREDICATES = Set.of(
            "budget_limit", "monthly_budget", "budget", "budget_amount");

    private static final String PRICE_PREDICATE = "subscription_price";
    private static final String CYCLE_PREDICATE = "subscription_cycle";

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        Optional<FactSnapshot> budgetFact = context.facts().stream()
                .filter(context::isActive)
                .filter(fact -> BUDGET_PREDICATES.contains(fact.predicate()))
                .filter(fact -> parseNumber(fact.objectValue()) > 0)
                .findFirst();
        if (budgetFact.isEmpty()) {
            return List.of();
        }

        Map<Long, String> cycleBySubject = new HashMap<>();
        for (FactSnapshot fact : context.facts()) {
            if (context.isActive(fact) && CYCLE_PREDICATE.equals(fact.predicate())) {
                cycleBySubject.put(fact.subjectId(), fact.objectValue());
            }
        }

        double monthlySpend = 0;
        List<Long> evidence = new ArrayList<>();
        if (budgetFact.get().id() != null) {
            evidence.add(budgetFact.get().id());
        }
        for (FactSnapshot fact : context.facts()) {
            if (!context.isActive(fact) || !PRICE_PREDICATE.equals(fact.predicate())) {
                continue;
            }
            double amount = parseNumber(fact.objectValue());
            if (amount <= 0) {
                continue;
            }
            monthlySpend += amount * cycleFactor(cycleBySubject.get(fact.subjectId()));
            if (fact.id() != null) {
                evidence.add(fact.id());
            }
        }
        if (monthlySpend <= 0) {
            return List.of();
        }

        double budget = parseNumber(budgetFact.get().objectValue());
        double ratio = monthlySpend / budget;
        if (ratio < 0.8) {
            return List.of();
        }
        String level = ratio >= 1.0 ? "high" : "mid";
        String ruleCode = ratio >= 1.0 ? "BUDGET_OVERSPENT" : "BUDGET_WARNING";
        long percent = Math.round(ratio * 100);
        String title = "订阅支出占月度预算 " + percent + "%";
        String description = "月度预算 " + format(budget) + "，订阅折合月度支出 "
                + format(monthlySpend) + "，占比 " + percent + "%。";
        return List.of(new RiskAnalysisRequest.DetectedRisk(
                "other", level, ruleCode, title, description,
                budgetFact.get().subjectId(), List.copyOf(evidence)));
    }

    private double cycleFactor(String cycle) {
        if (cycle == null) {
            return 1.0;
        }
        return switch (cycle) {
            case "quarterly" -> 1.0 / 3.0;
            case "yearly" -> 1.0 / 12.0;
            default -> 1.0;
        };
    }

    private double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = value.replaceAll("[¥￥,,\\s]", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }
}