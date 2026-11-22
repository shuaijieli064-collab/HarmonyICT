package com.nexus.ai.risk.rules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;
import com.nexus.ai.risk.RiskRule;

/**
 * 期限监控规则：任务截止、目标截止、项目截止。
 * 30/7/1 天三档预警，ruleCode 后缀体现命中档位，便于前端与运营统计。
 */
@Component
public class DeadlineRule implements RiskRule {

    private static final Set<String> PREDICATES = Set.of(
            "task_due_at", "goal_deadline", "project_deadline");

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        List<RiskAnalysisRequest.DetectedRisk> risks = new ArrayList<>();
        for (FactSnapshot fact : context.facts()) {
            if (!PREDICATES.contains(fact.predicate()) || !context.isActive(fact)) {
                continue;
            }
            Optional<Instant> due = context.dueOf(fact);
            if (due.isEmpty()) {
                continue;
            }
            long days = RiskEvaluationContext.daysUntil(context.analysisAt(), due.get());
            String level = RiskEvaluationContext.levelForDays(days);
            if (level == null) {
                continue;
            }
            String label = label(fact.predicate());
            String dueDate = RiskEvaluationContext.formatDate(due.get());
            String title = fact.subjectName() + "：" + label + (days == 0 ? "今天到期" : "将在 " + days + " 天内到期");
            String description = "事实「" + label + "」=" + fact.objectValue()
                    + "，到期日 " + dueDate + "，距分析基准时间剩余 " + days + " 天。";
            risks.add(new RiskAnalysisRequest.DetectedRisk(
                    "contract_deadline", level, "DEADLINE_" + RiskEvaluationContext.ruleSuffix(days),
                    title, description, fact.subjectId(), evidence(fact)));
        }
        return risks;
    }

    private String label(String predicate) {
        return switch (predicate) {
            case "task_due_at" -> "任务截止";
            case "goal_deadline" -> "目标截止";
            case "project_deadline" -> "项目截止";
            default -> predicate;
        };
    }

    private List<Long> evidence(FactSnapshot fact) {
        return fact.id() == null ? List.of() : List.of(fact.id());
    }
}