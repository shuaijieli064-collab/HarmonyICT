package com.nexus.ingestion.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.ExtractionProvider;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ingestion.storage.StorageProvider;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.world.service.WorldEntityService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 抽取入库编排服务（阶段四端到端联调核心链路）。
 * 上传成功后异步执行：文本提取（直读/OCR）→ AI 抽取（B 的 ExtractionProvider）→
 * 实体 find-or-create（归一化幂等）→ 事实落库（confidence/factStatus 透传，P0 拍板）→ 状态机回写。
 *
 * 状态机（契约 4.2，供 C 轮询）：pending → parsing → parsed / failed。
 * 幂等：已 parsing/parsed 的来源跳过重复触发；事实重抽由 P1-5 唯一键 + 服务层链式失效兜底。
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
    private final StorageProvider storageProvider;
    private final SourceTextExtractor textExtractor;
    private final ExtractionProvider extractionProvider;
    private final WorldEntityService entityService;
    private final WorldFactService factService;
    private final ObjectMapper objectMapper;

    public ExtractionPipelineService(WorldSourceMapper sourceMapper, StorageProvider storageProvider,
                                     SourceTextExtractor textExtractor, ExtractionProvider extractionProvider,
                                     WorldEntityService entityService, WorldFactService factService,
                                     ObjectMapper objectMapper) {
        this.sourceMapper = sourceMapper;
        this.storageProvider = storageProvider;
        this.textExtractor = textExtractor;
        this.extractionProvider = extractionProvider;
        this.entityService = entityService;
        this.factService = factService;
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
        for (ExtractionRequest.ExtractedFact fact : facts) {
            persistFact(userId, source.getId(), fact);
        }
    }

    private void persistFact(Long userId, Long sourceId, ExtractionRequest.ExtractedFact fact) {
        Map<String, Object> extra = fact.extra() == null ? Map.of() : fact.extra();
        String schemaEntityType = firstNonBlank(str(extra.get("entityType")), fact.subjectType());
        String entityType = mapEntityType(schemaEntityType);
        Long subjectId = entityService.findOrCreateByNormalizedName(userId, entityType, fact.subjectName(), "{}");

        factService.create(userId, new FactCreateRequest(
                subjectId,
                fact.predicate(),
                fact.objectValue(),
                str(extra.get("valueType")),
                str(extra.get("unit")),
                str(extra.get("normalizedValue")),
                serializeEvidence(extra.get("evidence")),
                sourceId,
                parseDate(extra.get("validFrom")),
                parseDate(extra.get("validUntil")),
                BigDecimal.valueOf(fact.confidence()),
                str(extra.get("factStatus"))));
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