package com.nexus.ai.extraction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.dto.ExtractionRequest;

/**
 * 解析并校验大模型输出，映射为 A 侧入库 DTO（ExtractionRequest.ExtractedFact）。
 * 为什么保留完整 JSON 校验：规范 6 要求结构化输出先按 extraction-schema.md 校验再入库，
 * 模型自由发挥的字段、越界置信度、无证据事实都在这里被拦下或降分。
 */
@Component
public class ExtractionOutputParser {

    private static final Set<String> PREDICATES = Set.of(
            "name", "purchased_at", "price", "currency", "merchant_name", "brand", "model",
            "serial_number", "order_id", "warranty_period", "warranty_until", "subscription_cycle",
            "subscription_start_at", "subscription_renewal_at", "subscription_end_at",
            "subscription_price", "auto_renew", "goal_target", "goal_deadline", "course_name",
            "course_schedule", "task_due_at", "task_status", "project_deadline", "location",
            "budget_limit");

    private static final Set<String> DATE_PREDICATES = Set.of(
            "purchased_at", "warranty_until", "subscription_start_at", "subscription_renewal_at",
            "subscription_end_at", "goal_deadline", "project_deadline");

    /**
     * factStatus 三态白名单（对齐 A 的 chk_fact_status_enum CHECK）。
     * 为什么在此校验：模型可能输出 confirmed/inferred/unverified 之外的值（如历史遗留的
     * extracted、或自造 verified），若直接透传会在入库时触发 DB CHECK 违规。此处统一降级
     * 为 unverified 并记告警，保证下游永远拿到合法三态。
     */
    private static final Set<String> FACT_STATUSES = Set.of("confirmed", "inferred", "unverified");

    /**
     * 本 Schema 的实体类型映射到 A 的 world_entities.entity_type
     * 枚举（person/asset/event/document/org）
     */
    private static final Map<String, String> ENTITY_TYPE_MAP = Map.ofEntries(
            Map.entry("device", "asset"),
            Map.entry("subscription", "asset"),
            Map.entry("account", "asset"),
            Map.entry("asset", "asset"),
            Map.entry("organization", "org"),
            Map.entry("person", "person"),
            Map.entry("goal", "event"),
            Map.entry("task", "event"),
            Map.entry("course", "event"),
            Map.entry("project", "event"),
            Map.entry("other", "event"));

    private final ObjectMapper objectMapper;

    public ExtractionOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedExtraction parse(String rawContent, double minConfidence) {
        JsonNode root = readJson(stripFence(rawContent));
        JsonNode factNodes = root.path("facts");
        if (!factNodes.isArray()) {
            throw new ExtractionSchemaException("缺少 facts 数组");
        }

        Map<String, JsonNode> entitiesById = new HashMap<>();
        Map<String, JsonNode> entitiesByName = new HashMap<>();
        JsonNode entities = root.path("entities");
        if (entities.isArray()) {
            for (JsonNode entity : entities) {
                String id = entity.path("entityId").asText("");
                String name = entity.path("name").asText("");
                if (!id.isBlank()) {
                    entitiesById.put(id, entity);
                }
                if (!name.isBlank()) {
                    entitiesByName.putIfAbsent(name, entity);
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        List<RawFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factNodes) {
            String subjectRef = node.path("subjectRef").asText("");
            String predicate = node.path("predicate").asText("");
            JsonNode objectNode = node.get("objectValue");
            if (subjectRef.isBlank() || objectNode == null || objectNode.isNull()) {
                warnings.add("W-MISSING-FIELD: 事实缺少 subjectRef 或 objectValue");
                continue;
            }
            if (!PREDICATES.contains(predicate)) {
                warnings.add("E-PREDICATE: 谓词不在受控词表 " + predicate);
                continue;
            }
            JsonNode entity = entitiesById.get(subjectRef);
            if (entity == null) {
                entity = entitiesByName.get(subjectRef);
            }
            if (entity == null) {
                warnings.add("W-NO-ENTITY: 事实主体无法解析 " + subjectRef);
                continue;
            }

            String objectValue = toObjectText(objectNode);
            String valueType = node.path("valueType").asText("string");
            double confidence = clamp(node.path("confidence").asDouble(0.50));
            String factStatus = normalizeFactStatus(node.path("factStatus").asText("confirmed"), predicate, warnings);
            String snippet = node.path("evidence").path("textSnippet").asText("");

            if (snippet.isBlank()) {
                confidence = Math.min(confidence, 0.49);
                warnings.add("E-NO-EVIDENCE: 事实缺少原文证据 " + predicate);
            }
            if ("inferred".equalsIgnoreCase(factStatus)) {
                confidence = clamp(confidence * 0.85);
                warnings.add("W-DATE-INFERRED: 推断事实已降置信度 " + predicate);
            }
            if (("date".equals(valueType) || DATE_PREDICATES.contains(predicate)) && !isValidDate(objectValue)) {
                confidence = clamp(confidence * 0.70);
                warnings.add("W-AMBIGUOUS-DATE: 日期无法归一 " + objectValue);
            }

            rawFacts.add(new RawFact(subjectRef, predicate, objectValue, valueType, confidence,
                    factStatus, node, entity));
        }

        Set<String> conflictKeys = detectConflicts(rawFacts, warnings);
        List<ExtractionRequest.ExtractedFact> facts = new ArrayList<>();
        for (RawFact raw : rawFacts) {
            double confidence = raw.confidence();
            if (conflictKeys.contains(key(raw))) {
                confidence = clamp(confidence - 0.10);
            }
            double rounded = Math.round(confidence * 100.0) / 100.0;
            if (rounded < minConfidence) {
                warnings.add("E-LOW-CONF: 置信度过低已丢弃 " + raw.predicate());
                continue;
            }
            facts.add(toExtractedFact(raw, rounded));
        }

        double overall = facts.isEmpty() ? 0
                : facts.stream().mapToDouble(ExtractionRequest.ExtractedFact::confidence).average().orElse(0);
        return new ParsedExtraction(List.copyOf(facts), Math.round(overall * 100.0) / 100.0, List.copyOf(warnings));
    }

    private Set<String> detectConflicts(List<RawFact> rawFacts, List<String> warnings) {
        Map<String, Set<String>> values = new HashMap<>();
        for (RawFact fact : rawFacts) {
            values.computeIfAbsent(key(fact), k -> new HashSet<>()).add(fact.objectValue());
        }
        Set<String> conflicts = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : values.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(entry.getKey());
                warnings.add("W-CONFLICT-VALUE: 同主体同谓词存在冲突值 " + entry.getKey());
            }
        }
        return conflicts;
    }

    private ExtractionRequest.ExtractedFact toExtractedFact(RawFact raw, double confidence) {
        JsonNode entity = raw.entity();
        String entityType = entity.path("entityType").asText("other");
        String subjectType = ENTITY_TYPE_MAP.getOrDefault(entityType, "event");
        String subjectName = entity.path("name").asText(raw.subjectRef());

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("entityId", raw.subjectRef());
        extra.put("entityType", entityType);
        extra.put("sourceId", raw.node().path("sourceId").asText(null));
        extra.put("valueType", raw.valueType());
        extra.put("unit", raw.node().path("unit").asText(null));
        extra.put("normalizedValue", raw.node().path("normalizedValue").asText(null));
        extra.put("factStatus", raw.factStatus());
        extra.put("validFrom", raw.node().path("validFrom").asText(null));
        extra.put("validUntil", raw.node().path("validUntil").asText(null));
        extra.put("evidence", evidenceMap(raw.node().path("evidence")));

        return new ExtractionRequest.ExtractedFact(
                subjectType, subjectName, raw.predicate(), raw.objectValue(), confidence, extra);
    }

    private Map<String, Object> evidenceMap(JsonNode evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("textSnippet", evidence.path("textSnippet").asText(""));
        map.put("page", evidence.path("page").isNumber() ? evidence.path("page").asInt() : null);
        return map;
    }

    private JsonNode readJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new ExtractionSchemaException("模型输出不是合法 JSON: " + e.getMessage());
        }
    }

    private String stripFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String toObjectText(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }
        return node.toString();
    }

    private boolean isValidDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * 归一化并校验 factStatus：合法三态原样返回（统一小写），非法值降级 unverified 并记告警。
     * 空值按 Schema 约定默认 confirmed（第 4.3 节 fact_status 必填，缺省视为原文直出）。
     */
    private String normalizeFactStatus(String raw, String predicate, List<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return "confirmed";
        }
        String normalized = raw.trim().toLowerCase();
        if (FACT_STATUSES.contains(normalized)) {
            return normalized;
        }
        warnings.add("W-INVALID-FACT-STATUS: 非法事实状态「" + raw + "」已降级 unverified " + predicate);
        return "unverified";
    }

    private String key(RawFact fact) {
        return fact.subjectRef() + "|" + fact.predicate();
    }

    private record RawFact(String subjectRef, String predicate, String objectValue, String valueType,
            double confidence, String factStatus, JsonNode node, JsonNode entity) {
    }

    public record ParsedExtraction(List<ExtractionRequest.ExtractedFact> facts,
            double overallConfidence, List<String> warnings) {
    }
}