package com.nexus.ai.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * 本地确定性抽取器（mock 模式使用）。
 * 为什么需要它：无华为云凭证时仍要能跑通「原文 → Schema JSON → 入库 DTO」链路，
 * 它用固定正则从原文提取，不含随机或写死的业务数据，输出完全由输入决定；
 * 真实演示必须切回 nexus.ai.mode=real 调用华为云 MaaS。
 */
@Component
public class RuleBasedExtractor {

    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})日?");
    private static final Pattern AMOUNT = Pattern.compile("[¥￥]\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern PRODUCT = Pattern.compile("商品名称[：:]\\s*([^\\n\\r]+)");
    private static final Pattern MERCHANT = Pattern.compile("销售方[：:]\\s*([^\\n\\r]+)");
    private static final Pattern WARRANTY = Pattern.compile(
            "保修[^\\n\\r。]{0,30}?(\\d+|[一二三四五六七八九十两]+)\\s*(年|个月|月)");
    private static final Pattern SUBSCRIPTION = Pattern.compile("您已开通([^\\n\\r，。]+)");
    private static final Pattern RENEWAL = Pattern.compile("下次续费日期[：:]\\s*(20\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern GOAL = Pattern.compile("目标[：:]\\s*([^\\n\\r]+)");
    private static final Pattern TASK = Pattern.compile("(\\d{1,2})月(\\d{1,2})日前完成([^\\n\\r]+)");

    private final ObjectMapper objectMapper;

    public RuleBasedExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extract(String sourceText) {
        return extract(sourceText, null);
    }

    public String extract(String sourceText, String sourceId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "1.0");
        List<Map<String, Object>> entities = new ArrayList<>();
        List<Map<String, Object>> facts = new ArrayList<>();
        List<Map<String, Object>> events = new ArrayList<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        String documentType = detectDocumentType(sourceText);
        root.put("documentType", documentType);

        String firstDate = findDate(sourceText);
        String entityType = "invoice".equals(documentType) ? "device"
                : "subscription".equals(documentType) ? "subscription" : "goal";
        String entityName = detectEntityName(sourceText, documentType);

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", "ent_1");
        entity.put("entityType", entityType);
        entity.put("name", entityName);
        entity.put("confidence", 0.90);
        entity.put("evidence", evidence(snippetOf(sourceText, 0, 60)));
        entities.add(entity);

        if ("invoice".equals(documentType)) {
            addFact(facts, "fact_1", "ent_1", "purchased_at", firstDate,
                    "date", null, firstDate, 0.92, "extracted", snippetOf(sourceText, 0, 80));
            Matcher amount = AMOUNT.matcher(sourceText);
            if (amount.find()) {
                String value = amount.group(1).replace(",", "");
                addFact(facts, "fact_2", "ent_1", "price", value,
                        "number", "CNY", value, 0.95, "extracted", amount.group());
            }
            Matcher merchant = MERCHANT.matcher(sourceText);
            if (merchant.find()) {
                addFact(facts, "fact_3", "ent_1", "merchant_name", merchant.group(1).trim(),
                        "string", null, null, 0.90, "extracted", merchant.group());
            }
            Matcher warranty = WARRANTY.matcher(sourceText);
            if (warranty.find()) {
                int period = parseChineseNumber(warranty.group(1));
                int months = "年".equals(warranty.group(2)) ? period * 12 : period;
                Map<String, Object> fact = fact("fact_4", "ent_1", "warranty_period", String.valueOf(months),
                        "duration", "month", "P" + months + "M", 0.78, "inferred", warranty.group());
                fact.put("validFrom", firstDate);
                fact.put("validUntil", plusMonths(firstDate, months));
                facts.add(fact);
                warnings.add(warning("W-DATE-INFERRED", "保修截止日由购买日与保修期推算", "fact_4"));
            }
            addEvent(events, "event_1", "purchase", "购买" + entityName, firstDate + "T00:00:00+08:00",
                    null, "ent_1", 0.92, snippetOf(sourceText, 0, 80));
        } else if ("subscription".equals(documentType)) {
            Matcher sub = SUBSCRIPTION.matcher(sourceText);
            if (sub.find()) {
                entity.put("name", sub.group(1).trim());
            }
            Matcher renewal = RENEWAL.matcher(sourceText);
            if (renewal.find()) {
                String due = renewal.group(1);
                addFact(facts, "fact_1", "ent_1", "subscription_renewal_at", due,
                        "date", null, due, 0.92, "extracted", renewal.group());
                addEvent(events, "event_1", "subscription_renewal", "会员自动续费",
                        null, due + "T00:00:00+08:00", "ent_1", 0.90, renewal.group());
            }
            addFact(facts, "fact_2", "ent_1", "subscription_cycle",
                    sourceText.contains("按月") ? "monthly" : "yearly",
                    "string", null, null, 0.90, "extracted", "订阅周期");
            addFact(facts, "fact_3", "ent_1", "auto_renew",
                    sourceText.contains("自动续费") ? "true" : "false",
                    "boolean", null, null, 0.90, "extracted", "自动续费");
        } else {
            Matcher goal = GOAL.matcher(sourceText);
            if (goal.find()) {
                entity.put("name", goal.group(1).trim());
                addFact(facts, "fact_1", "ent_1", "goal_target", goal.group(1).trim(),
                        "string", null, null, 0.88, "extracted", goal.group());
            }
            if (firstDate != null) {
                addFact(facts, "fact_2", "ent_1", "goal_deadline", firstDate,
                        "date", null, firstDate, 0.85, "extracted", firstDate);
            }
            Matcher task = TASK.matcher(sourceText);
            if (task.find()) {
                Map<String, Object> taskEntity = new LinkedHashMap<>();
                taskEntity.put("entityId", "ent_2");
                taskEntity.put("entityType", "task");
                taskEntity.put("name", task.group(3).trim());
                taskEntity.put("confidence", 0.88);
                taskEntity.put("evidence", evidence(task.group()));
                entities.add(taskEntity);
                String due = firstDate == null ? null : firstDate + "T23:59:00+08:00";
                addFact(facts, "fact_3", "ent_2", "task_due_at", due,
                        "datetime", null, due, 0.86, "extracted", task.group());
                Map<String, Object> taskNode = new LinkedHashMap<>();
                taskNode.put("taskId", "task_1");
                taskNode.put("title", task.group(3).trim());
                taskNode.put("dueAt", due);
                taskNode.put("priority", "high");
                taskNode.put("relatedEntityRefs", List.of("ent_2"));
                taskNode.put("confidence", 0.86);
                taskNode.put("evidence", evidence(task.group()));
                tasks.add(taskNode);
                addEvent(events, "event_2", "deadline", task.group(3).trim(),
                        null, due, "ent_2", 0.86, task.group());
            }
        }

        if (sourceId != null) {
            for (Map<String, Object> item : entities) {
                item.put("sourceId", sourceId);
            }
            for (Map<String, Object> item : facts) {
                item.put("sourceId", sourceId);
            }
            for (Map<String, Object> item : events) {
                item.put("sourceId", sourceId);
            }
            for (Map<String, Object> item : tasks) {
                item.put("sourceId", sourceId);
            }
        }

        root.put("entities", entities);
        root.put("facts", facts);
        root.put("events", events);
        root.put("tasks", tasks);
        root.put("warnings", warnings);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "本地抽取结果序列化失败");
        }
    }

    private void addFact(List<Map<String, Object>> facts, String id, String subjectRef, String predicate,
            String value, String valueType, String unit, String normalized,
            double confidence, String status, String snippet) {
        facts.add(fact(id, subjectRef, predicate, value, valueType, unit, normalized, confidence, status, snippet));
    }

    private Map<String, Object> fact(String id, String subjectRef, String predicate, String value,
            String valueType, String unit, String normalized,
            double confidence, String status, String snippet) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("factId", id);
        fact.put("subjectRef", subjectRef);
        fact.put("predicate", predicate);
        fact.put("objectValue", value);
        fact.put("valueType", valueType);
        fact.put("unit", unit);
        fact.put("normalizedValue", normalized);
        fact.put("confidence", confidence);
        fact.put("factStatus", status);
        fact.put("validFrom", null);
        fact.put("validUntil", null);
        fact.put("evidence", evidence(snippet));
        return fact;
    }

    private void addEvent(List<Map<String, Object>> events, String id, String type, String title,
            String startAt, String dueAt, String entityRef, double confidence, String snippet) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", id);
        event.put("eventType", type);
        event.put("title", title);
        event.put("startAt", startAt);
        event.put("endAt", null);
        event.put("dueAt", dueAt);
        event.put("relatedEntityRefs", List.of(entityRef));
        event.put("confidence", confidence);
        event.put("evidence", evidence(snippet));
        events.add(event);
    }

    private Map<String, Object> warning(String code, String message, String relatedRef) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", code);
        warning.put("message", message);
        warning.put("relatedRef", relatedRef);
        return warning;
    }

    private Map<String, Object> evidence(String snippet) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("textSnippet", snippet);
        evidence.put("page", 1);
        return evidence;
    }

    private String detectDocumentType(String text) {
        if (text.contains("发票") || text.contains("商品名称") || text.contains("价税合计")) {
            return "invoice";
        }
        if (text.contains("订阅") || text.contains("续费")) {
            return "subscription";
        }
        return "study_plan";
    }

    private String detectEntityName(String text, String documentType) {
        if ("invoice".equals(documentType)) {
            Matcher product = PRODUCT.matcher(text);
            return product.find() ? product.group(1).trim() : "未命名设备";
        }
        if ("subscription".equals(documentType)) {
            Matcher sub = SUBSCRIPTION.matcher(text);
            return sub.find() ? sub.group(1).trim() : "未命名订阅";
        }
        Matcher goal = GOAL.matcher(text);
        return goal.find() ? goal.group(1).trim() : "未命名目标";
    }

    private String findDate(String text) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return String.format("%s-%02d-%02d", matcher.group(1),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    private int parseChineseNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        Map<Character, Integer> digits = Map.of(
                '一', 1, '二', 2, '两', 2, '三', 3, '四', 4,
                '五', 5, '六', 6, '七', 7, '八', 8, '九', 9);
        if ("十".equals(value)) {
            return 10;
        }
        if (value.length() == 1) {
            return digits.getOrDefault(value.charAt(0), 0);
        }
        if (value.startsWith("十")) {
            return 10 + digits.getOrDefault(value.charAt(1), 0);
        }
        if (value.length() == 2 && value.charAt(1) == '十') {
            return digits.getOrDefault(value.charAt(0), 0) * 10;
        }
        if (value.length() == 3 && value.charAt(1) == '十') {
            return digits.getOrDefault(value.charAt(0), 0) * 10
                    + digits.getOrDefault(value.charAt(2), 0);
        }
        return 0;
    }

    private String plusMonths(String date, int months) {
        if (date == null) {
            return null;
        }
        return java.time.LocalDate.parse(date).plusMonths(months).toString();
    }

    private String snippetOf(String text, int from, int maxLength) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}