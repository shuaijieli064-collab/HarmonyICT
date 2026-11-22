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
 * 保修 / 订阅到期规则。与期限规则分开的原因：
 * 保修与订阅属于资产/合约生命周期，风险类型与运营动作不同（续保、取消自动续费、比价）。
 */
@Component
public class AssetExpiryRule implements RiskRule {

    private static final Set<String> WARRANTY_PREDICATES = Set.of("warranty_until");

    private static final Set<String> SUBSCRIPTION_PREDICATES = Set.of(
            "subscription_renewal_at", "subscription_end_at");

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        List<RiskAnalysisRequest.DetectedRisk> risks = new ArrayList<>();
        for (FactSnapshot fact : context.facts()) {
            boolean warranty = WARRANTY_PREDICATES.contains(fact.predicate());
            boolean subscription = SUBSCRIPTION_PREDICATES.contains(fact.predicate());
            if ((!warranty && !subscription) || !context.isActive(fact)) {
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
            String label = warranty ? "保修" : "订阅";
            String ruleCode = (warranty ? "WARRANTY_EXPIRY_" : "SUBSCRIPTION_EXPIRY_")
                    + RiskEvaluationContext.ruleSuffix(days);
            String dueDate = RiskEvaluationContext.formatDate(due.get());
            String title = fact.subjectName() + "：" + label
                    + (days == 0 ? "今天到期" : "将在 " + days + " 天内到期");
            String description = label + "到期事实「" + fact.predicate() + "」=" + fact.objectValue()
                    + "，到期日 " + dueDate + "，剩余 " + days + " 天。";
            risks.add(new RiskAnalysisRequest.DetectedRisk(
                    warranty ? "cert_expiry" : "contract_deadline", level, ruleCode,
                    title, description, fact.subjectId(), evidence(fact)));
        }
        return risks;
    }

    private List<Long> evidence(FactSnapshot fact) {
        return fact.id() == null ? List.of() : List.of(fact.id());
    }
}