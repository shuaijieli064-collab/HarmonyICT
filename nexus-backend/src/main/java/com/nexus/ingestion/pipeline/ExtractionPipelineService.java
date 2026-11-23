package com.nexus.ingestion.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.ExtractionProvider;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ingestion.storage.StorageProvider;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.world.service.WorldEntityService;
import com.nexus.world.service.WorldEventService;
import com.nexus.world.service.WorldFactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 抽取入库编排服务（阶段四端到端联调核心链路）。
 * 上传成功后异步执行：文本提取（直读/OCR）→ AI 抽取（B 的 ExtractionProvider）→
 * 实体 find-or-create（归一化幂等）→ 事实与事件落库 → 状态机回写。
 *
 * 状态机（契约 4.2，供 C 轮询）：pending → parsing → parsed / failed。
 * 幂等：已 parsing/parsed 的来源跳过重复触发；事实重抽由 P1-5 唯一键 + 服务层链式失效兜底；
 * 事件按 (user_id, source_id, title, occurred_at) 应用层查重（2026-11-23 拍板，不加 DDL）。
 * 红线：user_id 由服务端注入，禁止取自抽取结果；抽取失败不抛出到调用方（异步吞异常记日志）。
 */
@Component
public class ExtractionPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionPipelineService.class);

    /** 抽取目标提示（契约 4.2 建议），覆盖发票/订阅/保修/学习计划场景 */
    private static final List<String> SCHEMA_HINTS =
            List.of("assets", "subscriptions", "warranties", "income", "goals", "courses");

    private static final Map<String, String> ENTITY_TYPE_MAPPING = Map.of(
            "device", "asset",
            "subscription", "asset",
            "account", "asset",
            "asset", "asset",
            "organization", "org",
            "person", "person");

    private final WorldSourceMapper sourceMapper;
    private final WorldEventMapper eventMapper;
    private final StorageProvider storageProvider;
    private final SourceTextExtractor textExtractor;
    private final ExtractionProvider extractionProvider;
    private final WorldEntityService entityService;
    private final WorldFactService factService;
    private final WorldEventService eventService;
    private final ObjectMapper objectMapper;

    public ExtractionPipelineService(WorldSourceMapper sourceMapper, WorldEventMapper eventMapper,
                                     StorageProvider storageProvider, SourceTextExtractor textExtractor,
                                     ExtractionProvider extractionProvider, WorldEntityService entityService,
                                     WorldFactService factService, WorldEventService eventService,
                                     ObjectMapper objectMapper) {
        this.sourceMapper = sourceMapper;
        this.eventMapper = eventMapper;
        this.storageProvider = storageProvider;
        this.textExtractor = textExtractor;
        this.extractionProvider = extractionProvider;
        this.entityService = entityService;
        this.factService = factService;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行抽取入库链路。由上传服务在落库后调用（跨 Bean 调用走 @Async 代理）。
     */
    @Async
    public void processAsync(Long userId, Long sourceId) {
        WorldSource source = sourceMapper.selectById(sourceId);
        if (source == null || !source.getUserId().equals(userId)) {
            log.warn("抽取编排跳过：来源不存在或不属于当前用户 sourceId={} userId={}", sourceId, userId);
            return;
        }
        if (!"pending".equals(source.getStatus())) {
            log.info("抽取编排跳过：来源状态 {} 非 pending sourceId={}", source.getStatus(), sourceId);
            return;
        }
        try {
            markStatus(source, "parsing");
            runPipeline(userId, source);
            markStatus(source, "parsed");
            log.info("抽取编排完成 sourceId={} userId={}", sourceId, userId);
        } catch (Exception e) {
            log.error("抽取编排失败 sourceId={} userId={}", sourceId, userId, e);
            markStatus(source, "failed");
        }
    }

    private void runPipeline(Long userId, WorldSource source) {
        String extension = extensionOf(source.getFileName());
        byte[] content = storageProvider.load(userId, source.getObsKey());
        SourceTextExtractor.ExtractedText text = textExtractor.extract(extension, content);
        if (text.maxPage() > 0) {
            source.setPageCount(text.maxPage());
        }

        ExtractionRequest.ExtractionResult result = extractionProvider.extract(
                new ExtractionRequest(source.getId().toString(), text.fullText(), SCHEMA_HINTS));

        List<ExtractionRequest.ExtractedFact> facts = result == null || result.facts() == null
                ? List.of() : result.facts();
        Map<String, Long> entityIds = new HashMap<>();
        for (ExtractionRequest.ExtractedFact fact : facts) {
            Long subjectId = persistFact(userId, source.getId(), fact);
            Map<String, Object> extra = fact.extra() == null ? Map.of() : fact.extra();
            String tempEntityId = strOf(extra, "entity_id", "entityId");
            if (subjectId != null && tempEntityId != null) {
                entityIds.putIfAbsent(tempEntityId, subjectId);
            }
        }

        // 事件依赖事实阶段建立的实体映射，故必须在事实全部落库后执行。
        List<ExtractionRequest.ExtractedEvent> events = result == null || result.events() == null
                ? List.of() : result.events();
        for (ExtractionRequest.ExtractedEvent event : events) {
            persistEvent(userId, source.getId(), event, entityIds);
        }
    }

    private Long persistFact(Long userId, Long sourceId, ExtractionRequest.ExtractedFact fact) {
        Map<String, Object> extra = fact.extra() == null ? Map.of() : fact.extra();
        String schemaEntityType = firstNonBlank(
                strOf(extra, "entity_type", "entityType"), fact.subjectType());
        String entityType = mapEntityType(schemaEntityType);
        Long subjectId = entityService.findOrCreateByNormalizedName(userId, entityType, fact.subjectName(), "{}");

        factService.create(userId, new FactCreateRequest(
                subjectId,
                fact.predicate(),
                fact.objectValue(),
                strOf(extra, "value_type", "valueType"),
                strOf(extra, "unit", "unit"),
                strOf(extra, "normalized_value", "normalizedValue"),
                serializeEvidence(extra.get("evidence")),
                sourceId,
                parseDate(strOf(extra, "valid_from", "validFrom")),
                parseDate(strOf(extra, "valid_until", "validUntil")),
                BigDecimal.valueOf(fact.confidence()),
                strOf(extra, "fact_status", "factStatus")));
        return subjectId;
    }

    /**
     * 事件落库。按拍板口径：应用层查重、无 DDL；无时间字段跳过；单条失败隔离。
     */
    private void persistEvent(Long userId, Long sourceId, ExtractionRequest.ExtractedEvent event,
                              Map<String, Long> entityIds) {
        try {
            Long eventSourceId = parseSourceId(event.sourceId(), sourceId);
            if (eventSourceId == null) {
                log.warn("事件跳过：source_id 非法 eventId={} sourceId={}", event.eventId(), event.sourceId());
                return;
            }
            if (!eventSourceId.equals(sourceId)) {
                WorldSource eventSource = sourceMapper.selectById(eventSourceId);
                if (eventSource == null || !userId.equals(eventSource.getUserId())) {
                    log.warn("事件跳过：来源不属于当前用户 eventId={} sourceId={}", event.eventId(), eventSourceId);
                    return;
                }
            }

            Instant occurredAt = firstInstant(event.startAt(), event.dueAt(), event.endAt());
            if (occurredAt == null) {
                log.warn("事件跳过：缺少可归一时间 eventId={}", event.eventId());
                return;
            }
            Long entityId = resolveEntityId(event.relatedEntityRefs(), entityIds);
            String description = evidenceSnippet(event.evidence());
            if (description == null) {
                log.warn("事件跳过：缺少原文证据 eventId={}", event.eventId());
                return;
            }

            Long duplicated = eventMapper.selectCount(new LambdaQueryWrapper<WorldEvent>()
                    .eq(WorldEvent::getUserId, userId)
                    .eq(WorldEvent::getSourceId, sourceId)
                    .eq(WorldEvent::getTitle, event.title())
                    .eq(WorldEvent::getOccurredAt, occurredAt));
            if (duplicated != null && duplicated > 0) {
                log.info("事件跳过：同源同标题同时间已存在 eventId={} title={}", event.eventId(), event.title());
                return;
            }

            eventService.create(userId, new EventCreateRequest(
                    entityId, event.eventType(), event.title(), description, occurredAt, sourceId));
        } catch (Exception e) {
            // 单条事件失败不得影响同批事实与后续事件，也不改变来源状态。
            log.warn("单条事件入库失败已跳过 eventId={} reason={}", event.eventId(), e.getMessage());
        }
    }

    private Long parseSourceId(String raw, Long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** occurred_at 优先级：start_at > due_at > end_at（Parser 已归一为 UTC 字符串）。 */
    private Instant firstInstant(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e) {
                log.warn("事件时间解析失败，尝试下一个候选: {}", value);
            }
        }
        return null;
    }

    /** 仅解析 ent_* 引用；fact_* 引用按拍板口径忽略。 */
    private Long resolveEntityId(List<String> refs, Map<String, Long> entityIds) {
        if (refs == null) {
            return null;
        }
        for (String ref : refs) {
            if (ref != null && ref.startsWith("ent_")) {
                Long id = entityIds.get(ref);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private String evidenceSnippet(Map<String, Object> evidence) {
        if (evidence == null) {
            return null;
        }
        Object raw = evidence.containsKey("text_snippet") ? evidence.get("text_snippet") : evidence.get("textSnippet");
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        String snippet = raw.toString();
        return snippet.length() <= 200 ? snippet : snippet.substring(0, 200);
    }

    /** extraction-schema 10.6：device/subscription/account/asset → asset；organization → org；person → person；其余 → event */
    private String mapEntityType(String schemaEntityType) {
        if (schemaEntityType == null || schemaEntityType.isBlank()) {
            return "event";
        }
        return ENTITY_TYPE_MAPPING.getOrDefault(schemaEntityType.toLowerCase(Locale.ROOT), "event");
    }

    private String serializeEvidence(Object evidence) {
        if (evidence == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            log.warn("evidence 序列化失败，降级为 null: {}", e.getMessage());
            return null;
        }
    }

    /** YYYY-MM-DD → Instant（UTC 零点）；解析失败返回 null，不阻断链路 */
    private Instant parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString()).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("抽取日期解析失败，忽略该字段: {}", value);
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    /** snake_case 优先，camelCase 兜底（临时兼容，验收后移除） */
    private String strOf(Map<String, Object> map, String snake, String camel) {
        Object value = map.containsKey(snake) ? map.get(snake) : map.get(camel);
        return str(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private void markStatus(WorldSource source, String status) {
        source.setStatus(status);
        source.setUpdatedAt(Instant.now());
        sourceMapper.updateById(source);
    }

    private String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
