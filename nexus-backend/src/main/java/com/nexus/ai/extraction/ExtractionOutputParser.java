package com.nexus.ai.extraction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.dto.ExtractionRequest;

/**
 * 解析并校验大模型输出，映射为 A 侧入库 DTO（ExtractedFact / ExtractedEvent）。
 * 为什么保留完整 JSON 校验：规范 6 要求结构化输出先按 extraction-schema.md 校验再入库，
 * 模型自由发挥的字段、越界置信度、无证据事实都在这里被拦下或降分。
 *
 * 命名口径（2026-11-23 拍板）：snake_case 为规范键，camelCase 仅作兼容兜底读取；
 * 兼容兜底在 B 契约复核与端到端验收后移除。
 */
@Component
public class ExtractionOutputParser {

    private static final Set<String> PREDICATES = Set.of(
            "name", "purchased_at", "price", "currency", "merchant_name", "brand", "model",
            "serial_number", "order_id", "warranty_period", "warranty_until", "subscription_cycle",
            "subscription_start_at", "subscription_renewal_at", "subscription_end_at",
            "subscription_price", "auto_renew", "goal_target", "goal_deadline", "course_name",
            "course_schedule", "task_due_at", "task_status", "project_deadline", "location");

    private static final Set<String> DATE_PREDICATES = Set.of(
            "purchased_at", "warranty_until", "subscription_start_at", "subscription_renewal_at",
            "subscription_end_at", "goal_deadline", "project_deadline");

    /** 事件置信度硬门槛：低于此值不入库（不随 facts 的 minConfidence 配置浮动） */
    private static final double MIN_EVENT_CONFIDENCE = 0.50;

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

    /**
     * event_type 10→6 映射（extraction-schema 第 4.4 节 → database-design 第 3 节）。
     * world_events.chk_event_type 仅允许 6 值，10 值枚举直接入库会触发 CHECK 违规。
     * B 的 EVENTS-INGESTION-CONTRACT.md push 后须复核本表。
     */
    private static final Map<String, String> EVENT_TYPE_MAP = Map.ofEntries(
            Map.entry("purchase", "asset"),
            Map.entry("payment", "contract"),
            Map.entry("subscription_start", "contract"),
            Map.entry("subscription_renewal", "contract"),
            Map.entry("subscription_expiry", "contract"),
            Map.entry("warranty_expiry", "asset"),
            Map.entry("deadline", "other"),
            Map.entry("class", "education"),
            Map.entry("exam", "education"),
            Map.entry("other", "other"));

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
                String id = text(entity, "entity_id", "entityId");
                String name = text(entity, "name", "name");
                if (id != null && !id.isBlank()) {
                    entitiesById.put(id, entity);
                }
                if (name != null && !name.isBlank()) {
                    entitiesByName.putIfAbsent(name, entity);
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        List<RawFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factNodes) {
            String subjectRef = text(node, "subject_ref", "subjectRef");
            String predicate = text(node, "predicate", "predicate");
            JsonNode objectNode = field(node, "object_value", "objectValue");
            if (subjectRef == null || subjectRef.isBlank() || objectNode == null || objectNode.isNull()) {
                warnings.add("W-MISSING-FIELD: 事实缺少 subject_ref 或 object_value");
                continue;
            }
            if (predicate == null || !PREDICATES.contains(predicate)) {
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
            String rawValueType = text(node, "value_type", "valueType");
            String valueType = rawValueType == null ? "string" : rawValueType;
            double confidence = clamp(node.path("confidence").asDouble(0.50));
            String factStatus = normalizeFactStatus(text(node, "fact_status", "factStatus"), predicate, warnings);
            Map<String, Object> evidence = evidenceMap(field(node, "evidence", "evidence"));
            String snippet = (String) evidence.get("text_snippet");

            if (snippet == null || snippet.isBlank()) {
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
        List<ExtractionRequest.ExtractedEvent> events = parseEvents(root, warnings);

        String extractionId = text(root, "extraction_id", "extractionId");
        String language = text(root, "language", "language");
        String extractedAt = text(root, "extracted_at", "extractedAt");
        Map<String, Object> source = objectMap(field(root, "source", "source"));

        return new ParsedExtraction(List.copyOf(facts), Math.round(overall * 100.0) / 100.0,
                events, extractionId, source, language, extractedAt, List.copyOf(warnings));
    }

    /** 解析 events 数组：映射 event_type、归一时间、校验证据与置信度；单条失败只记告警。 */
    private List<ExtractionRequest.ExtractedEvent> parseEvents(JsonNode root, List<String> warnings) {
        JsonNode eventNodes = root.path("events");
        if (!eventNodes.isArray()) {
            return List.of();
        }
        List<ExtractionRequest.ExtractedEvent> events = new ArrayList<>();
        for (JsonNode node : eventNodes) {
            String eventId = text(node, "event_id", "eventId");
            String rawType = text(node, "event_type", "eventType");
            String title = text(node, "title", "title");
            if (eventId == null || rawType == null || title == null || title.isBlank()) {
                warnings.add("W-MISSING-FIELD: 事件缺少 event_id / event_type / title");
                continue;
            }
            String eventType = EVENT_TYPE_MAP.get(rawType.toLowerCase(Locale.ROOT));
            if (eventType == null) {
                warnings.add("W-UNSUPPORTED-EVENT-TYPE: 未知事件类型已降级 other " + rawType);
                eventType = "other";
            }
            Map<String, Object> evidence = evidenceMap(field(node, "evidence", "evidence"));
            String snippet = (String) evidence.get("text_snippet");
            if (snippet == null || snippet.isBlank()) {
                warnings.add("E-NO-EVIDENCE: 事件缺少原文证据 " + eventId);
                continue;
            }
            double confidence = clamp(node.path("confidence").asDouble(0.50));
            double rounded = Math.round(confidence * 100.0) / 100.0;
            if (rounded < MIN_EVENT_CONFIDENCE) {
                warnings.add("E-LOW-CONF: 事件置信度过低已丢弃 " + eventId);
                continue;
            }
            events.add(new ExtractionRequest.ExtractedEvent(
                    eventId,
                    eventType,
                    title,
                    normalizeTime(text(node, "start_at", "startAt"), warnings),
                    normalizeTime(text(node, "end_at", "endAt"), warnings),
                    normalizeTime(text(node, "due_at", "dueAt"), warnings),
                    arrayText(field(node, "related_entity_refs", "relatedEntityRefs")),
                    rounded,
                    text(node, "source_id", "sourceId"),
                    Map.copyOf(evidence)));
        }
        return List.copyOf(events);
    }

    /** 无时区按 +08:00 处理并记告警；统一输出 UTC Instant 字符串。 */
    private String normalizeTime(String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toString();
        } catch (DateTimeParseException e) {
            try {
                warnings.add("W-TZ-ASSUMED: 事件时间无时区，按 +08:00 处理 " + value);
                return LocalDateTime.parse(value).atOffset(ZoneOffset.ofHours(8)).toInstant().toString();
            } catch (DateTimeParseException e2) {
                warnings.add("W-AMBIGUOUS-DATE: 事件时间无法归一 " + value);
                return null;
            }
        }
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
        String rawEntityType = text(entity, "entity_type", "entityType");
        String entityType = rawEntityType == null ? "other" : rawEntityType;
        String subjectType = ENTITY_TYPE_MAP.getOrDefault(entityType, "event");
        String rawSubjectName = text(entity, "name", "name");
        String subjectName = rawSubjectName == null ? raw.subjectRef() : rawSubjectName;

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("entity_id", raw.subjectRef());
        extra.put("entity_type", entityType);
        extra.put("source_id", text(raw.node(), "source_id", "sourceId"));
        extra.put("value_type", raw.valueType());
        extra.put("unit", text(raw.node(), "unit", "unit"));
        extra.put("normalized_value", text(raw.node(), "normalized_value", "normalizedValue"));
        extra.put("fact_status", raw.factStatus());
        extra.put("valid_from", text(raw.node(), "valid_from", "validFrom"));
        extra.put("valid_until", text(raw.node(), "valid_until", "validUntil"));
        extra.put("evidence", evidenceMap(field(raw.node(), "evidence", "evidence")));

        return new ExtractionRequest.ExtractedFact(
                subjectType, subjectName, raw.predicate(), raw.objectValue(), confidence, extra);
    }

    private Map<String, Object> evidenceMap(JsonNode evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        String snippet = text(evidence, "text_snippet", "textSnippet");
        if (snippet != null) {
            map.put("text_snippet", snippet);
        }
        if (evidence != null && evidence.path("page").isNumber()) {
            map.put("page", evidence.path("page").asInt());
        }
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
            java.time.LocalDate.parse(value);
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

    /** snake_case 优先，camelCase 兜底（临时兼容，验收后移除） */
    private static JsonNode field(JsonNode node, String snake, String camel) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(snake);
        if (value == null || value.isNull()) {
            value = node.get(camel);
        }
        return value == null || value.isNull() ? null : value;
    }

    /** snake_case 优先，camelCase 兜底；空串按缺失处理 */
    private static String text(JsonNode node, String snake, String camel) {
        JsonNode value = field(node, snake, camel);
        if (value == null) {
            return null;
        }
        String result = value.asText();
        return result.isEmpty() ? null : result;
    }

    private static List<String> arrayText(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(result);
    }

    private record RawFact(String subjectRef, String predicate, String objectValue, String valueType,
            double confidence, String factStatus, JsonNode node, JsonNode entity) {
    }

    public record ParsedExtraction(List<ExtractionRequest.ExtractedFact> facts,
            double overallConfidence,
            List<ExtractionRequest.ExtractedEvent> events,
            String extractionId,
            Map<String, Object> source,
            String language,
            String extractedAt,
            List<String> warnings) {

        /** 兼容旧调用（仅需 facts / confidence / warnings） */
        public ParsedExtraction(List<ExtractionRequest.ExtractedFact> facts, double overallConfidence,
                List<String> warnings) {
            this(facts, overallConfidence, List.of(), null, Map.of(), null, null, warnings);
        }
    }
}
