package com.nexus.ai.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.risk.RiskEvaluationContext;

/**
 * 意图解析（规则驱动）。为什么不让模型直接决定动作：Agent 涉及写操作，
 * 规则解析结果可复现、可测试、可审计；模型只用于后续解释文本，不决定级别与目标。
 */
@Component
public class AgentIntentParser {

    private static final Pattern DATE = Pattern.compile(
            "(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})日?");

    private static final Pattern REQUESTED_VALUE = Pattern.compile(
            "(?:改成|改为|改到|更新为|设为)\\s*([^，。；,;\\s]+)");

    private static final Set<String> UPDATE_KEYWORDS = Set.of(
            "更新", "修改", "改成", "改为", "改到", "纠正");

    private static final Set<String> CREATE_TASK_KEYWORDS = Set.of(
            "待办", "任务", "提醒", "安排", "计划");

    /** 日程关键词须在 CREATE_TASK 之前判定：「安排日程」应建事件而非待办 */
    private static final Set<String> CREATE_EVENT_KEYWORDS = Set.of(
            "日程", "日历", "会议", "事件");

    private static final Set<String> SUMMARY_KEYWORDS = Set.of(
            "查看", "汇总", "整理", "列出", "有哪些", "概览", "风险");

    public ParsedIntent parse(String intent, List<FactSnapshot> facts) {
        String text = intent == null ? "" : intent;
        IntentType type = detectType(text);
        List<FactSnapshot> safeFacts = facts == null ? List.of() : facts;
        String targetDate = extractDate(text);
        String requestedValue = extractRequestedValue(text, targetDate);

        FactSnapshot target = switch (type) {
            case UPDATE_FACT -> locateFact(text, safeFacts, requestedValue);
            case CREATE_TASK -> nearestDueFact(text, safeFacts);
            case CREATE_EVENT -> locateFact(text, safeFacts, null);
            default -> null;
        };
        return new ParsedIntent(type, target, targetDate, requestedValue);
    }

    private IntentType detectType(String text) {
        if (containsAny(text, UPDATE_KEYWORDS)) {
            return IntentType.UPDATE_FACT;
        }
        if (containsAny(text, CREATE_EVENT_KEYWORDS)) {
            return IntentType.CREATE_EVENT;
        }
        if (containsAny(text, CREATE_TASK_KEYWORDS)) {
            return IntentType.CREATE_TASK;
        }
        if (containsAny(text, SUMMARY_KEYWORDS)) {
            return IntentType.SUMMARY;
        }
        return IntentType.SUMMARY;
    }

    private FactSnapshot locateFact(String text, List<FactSnapshot> facts, String requestedValue) {
        List<FactSnapshot> candidates = new ArrayList<>();
        for (FactSnapshot fact : facts) {
            if (!matchesPredicateHint(text, fact.predicate())) {
                continue;
            }
            if (matchesEntity(text, fact.subjectName())) {
                candidates.add(fact);
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (!candidates.isEmpty()) {
            // 多候选时按「新值语义」消歧：数值→价格类谓词；日期→截止/到期类取最近到期；仍不唯一则放弃
            return disambiguate(candidates, requestedValue);
        }

        List<FactSnapshot> byEntity = facts.stream()
                .filter(fact -> matchesEntity(text, fact.subjectName()))
                .toList();
        return byEntity.size() == 1 ? byEntity.get(0) : null;
    }

    /** 多候选消歧：新值是数字选价格类，是日期选最近到期的截止类；无法判定返回 null（宁缺勿错）。 */
    private FactSnapshot disambiguate(List<FactSnapshot> candidates, String requestedValue) {
        if (requestedValue == null || requestedValue.isBlank()) {
            return null;
        }
        if (requestedValue.matches("\\d+(\\.\\d+)?")) {
            return candidates.stream()
                    .filter(fact -> fact.predicate() != null && fact.predicate().contains("price"))
                    .findFirst()
                    .orElse(null);
        }
        Optional<Instant> requestedDate = RiskEvaluationContext.parseInstant(requestedValue);
        if (requestedDate.isPresent()) {
            // 截止/到期类事实：validUntil 优先，否则解析 objectValue；取最近到期者
            return candidates.stream()
                    .filter(fact -> fact.validUntil() != null
                            || RiskEvaluationContext.parseInstant(fact.objectValue()).isPresent())
                    .min(java.util.Comparator.comparing(
                            fact -> fact.validUntil() != null
                                    ? fact.validUntil()
                                    : RiskEvaluationContext.parseInstant(fact.objectValue()).orElseThrow()))
                    .orElse(null);
        }
        return null;
    }

    private FactSnapshot nearestDueFact(String text, List<FactSnapshot> facts) {
        Instant now = Instant.now();
        FactSnapshot nearest = null;
        Instant nearestDue = null;
        for (FactSnapshot fact : facts) {
            boolean predicateMatches = matchesPredicateHint(text, fact.predicate());
            boolean genericDue = fact.validUntil() != null || isDuePredicate(fact.predicate());
            if (!predicateMatches && !genericDue) {
                continue;
            }
            Optional<Instant> due = RiskEvaluationContext.parseInstant(fact.objectValue());
            if (due.isEmpty() && fact.validUntil() != null) {
                due = Optional.of(fact.validUntil());
            }
            if (due.isEmpty() || due.get().isBefore(now)) {
                continue;
            }
            if (nearestDue == null || due.get().isBefore(nearestDue)) {
                nearest = fact;
                nearestDue = due.get();
            }
        }
        return nearest;
    }

    private boolean matchesPredicateHint(String text, String predicate) {
        if (predicate == null) {
            return false;
        }
        if (text.contains("保修") && "warranty_until".equals(predicate)) {
            return true;
        }
        if ((text.contains("续费") || text.contains("订阅"))
                && ("subscription_renewal_at".equals(predicate) || "subscription_end_at".equals(predicate))) {
            return true;
        }
        if ((text.contains("价格") || text.contains("费用"))
                && ("price".equals(predicate) || "subscription_price".equals(predicate))) {
            return true;
        }
        if (text.contains("目标") && "goal_target".equals(predicate)) {
            return true;
        }
        if ((text.contains("截止") || text.contains("到期")) && isDuePredicate(predicate)) {
            return true;
        }
        return false;
    }

    private boolean isDuePredicate(String predicate) {
        return Set.of("task_due_at", "goal_deadline", "project_deadline",
                "warranty_until", "subscription_renewal_at", "subscription_end_at").contains(predicate);
    }

    private boolean matchesEntity(String text, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (text.contains(name)) {
            return true;
        }
        if (name.length() < 4) {
            return false;
        }
        for (int i = 0; i + 4 <= name.length(); i++) {
            if (text.contains(name.substring(i, i + 4))) {
                return true;
            }
        }
        return false;
    }

    private String extractDate(String text) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return String.format("%s-%02d-%02d", matcher.group(1),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    private String extractRequestedValue(String text, String targetDate) {
        if (targetDate != null) {
            return targetDate;
        }
        Matcher matcher = REQUESTED_VALUE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    public enum IntentType {
        SUMMARY, CREATE_TASK, CREATE_EVENT, UPDATE_FACT, UNKNOWN
    }

    /**
     * @param targetFact     匹配到的目标事实，未定位时为 null
     * @param targetDate     意图中的日期（YYYY-MM-DD）
     * @param requestedValue 用户要求写入的新值
     */
    public record ParsedIntent(IntentType type, FactSnapshot targetFact,
            String targetDate, String requestedValue) {
    }
}