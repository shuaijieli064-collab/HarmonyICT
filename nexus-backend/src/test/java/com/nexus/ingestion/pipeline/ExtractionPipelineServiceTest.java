package com.nexus.ingestion.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.ExtractionProvider;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ai.dto.OcrRequest;
import com.nexus.ingestion.storage.StorageProvider;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.world.service.WorldEntityService;
import com.nexus.world.service.WorldEventService;
import com.nexus.world.service.WorldFactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段四编排链路验证：上传 → 异步抽取 → 实体 find-or-create → 事实与事件落库 → 状态机。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtractionPipelineServiceTest {

    @Mock
    private WorldSourceMapper sourceMapper;

    @Mock
    private WorldEventMapper eventMapper;

    @Mock
    private StorageProvider storageProvider;

    @Mock
    private SourceTextExtractor textExtractor;

    @Mock
    private ExtractionProvider extractionProvider;

    @Mock
    private WorldEntityService entityService;

    @Mock
    private WorldFactService factService;

    @Mock
    private WorldEventService eventService;

    private ExtractionPipelineService pipeline;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        pipeline = new ExtractionPipelineService(sourceMapper, eventMapper, storageProvider, textExtractor,
                extractionProvider, entityService, factService, eventService, objectMapper);
        when(sourceMapper.selectById(9L)).thenReturn(pendingSource());
        when(sourceMapper.updateById(any(WorldSource.class))).thenReturn(1);
        when(storageProvider.load(1L, "key-9")).thenReturn("stub".getBytes(StandardCharsets.UTF_8));
        when(entityService.findOrCreateByNormalizedName(any(), anyString(), anyString(), anyString()))
                .thenReturn(100L);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        // 事实 extra 保留 camelCase，用于验证兼容兜底读取
        when(extractionProvider.extract(any())).thenReturn(new ExtractionRequest.ExtractionResult(
                List.of(new ExtractionRequest.ExtractedFact("device", "ThinkPad X1", "price", "8999.00",
                                0.97, Map.of("entityType", "device", "valueType", "number", "unit", "CNY",
                                "normalizedValue", "8999.00", "factStatus", "confirmed",
                                "validFrom", "2026-11-15", "evidence", Map.of("textSnippet", "价格 8999", "page", 2))),
                        new ExtractionRequest.ExtractedFact("organization", "联想", "brand", "ThinkPad",
                                0.9, Map.of("entityType", "organization", "valueType", "string",
                                "factStatus", "inferred"))), 0.93));
    }

    private WorldSource pendingSource() {
        WorldSource source = new WorldSource();
        source.setId(9L);
        source.setUserId(1L);
        source.setSourceType("upload");
        source.setFileName("发票.pdf");
        source.setObsKey("key-9");
        source.setStatus("pending");
        return source;
    }

    private ArgumentCaptor<WorldSource> runAndCaptureSource() {
        pipeline.processAsync(1L, 9L);
        ArgumentCaptor<WorldSource> captor = ArgumentCaptor.forClass(WorldSource.class);
        verify(sourceMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        return captor;
    }

    @Test
    void pipelinePersistsFactsWithEntityTypeMappingAndStatusParsed() {
        when(textExtractor.extract(eq("pdf"), any())).thenReturn(
                new SourceTextExtractor.ExtractedText("全文内容",
                        List.of(new OcrRequest.OcrResult.TextBlock(1, "第一页", 0.9),
                                new OcrRequest.OcrResult.TextBlock(2, "第二页", 0.9)),
                        0.9));

        ArgumentCaptor<WorldSource> sourceCaptor = runAndCaptureSource();
        assertEquals("parsed", sourceCaptor.getValue().getStatus());
        assertEquals(2, sourceCaptor.getValue().getPageCount());

        // entityType 映射：device→asset、organization→org（extraction-schema 10.6）
        verify(entityService).findOrCreateByNormalizedName(1L, "asset", "ThinkPad X1", "{}");
        verify(entityService).findOrCreateByNormalizedName(1L, "org", "联想", "{}");

        ArgumentCaptor<FactCreateRequest> factCaptor = ArgumentCaptor.forClass(FactCreateRequest.class);
        verify(factService, org.mockito.Mockito.times(2)).create(eq(1L), factCaptor.capture());
        List<FactCreateRequest> created = factCaptor.getAllValues();

        FactCreateRequest price = created.get(0);
        assertEquals(100L, price.subjectId());
        assertEquals("price", price.predicate());
        assertEquals("number", price.valueType());
        assertEquals("CNY", price.unit());
        assertEquals(9L, price.sourceId());
        assertEquals(new BigDecimal("0.97"), price.confidence());
        assertEquals("confirmed", price.factStatus());
        assertNotNull(price.validFrom());
        assertNotNull(price.evidence());

        FactCreateRequest brand = created.get(1);
        assertEquals("inferred", brand.factStatus());
        assertNull(brand.validFrom());
    }

    @Test
    void pipelineMarksFailedWhenExtractionThrows() {
        when(textExtractor.extract(anyString(), any())).thenReturn(
                new SourceTextExtractor.ExtractedText("全文内容", null, null));
        when(extractionProvider.extract(any()))
                .thenThrow(new com.nexus.common.exception.BizException(
                        com.nexus.common.result.ResultCode.EXTRACTION_SCHEMA));

        ArgumentCaptor<WorldSource> captor = runAndCaptureSource();
        assertEquals("failed", captor.getValue().getStatus());
        verify(factService, org.mockito.Mockito.never()).create(any(), any());
    }

    @Test
    void pipelineSkipsNonPendingSource() {
        WorldSource parsed = pendingSource();
        parsed.setStatus("parsed");
        when(sourceMapper.selectById(9L)).thenReturn(parsed);

        pipeline.processAsync(1L, 9L);

        verify(sourceMapper, org.mockito.Mockito.never()).updateById(any(WorldSource.class));
        verify(extractionProvider, org.mockito.Mockito.never()).extract(any());
    }

    @Test
    void pipelineRejectsForeignSource() {
        when(sourceMapper.selectById(9L)).thenReturn(pendingSource());

        pipeline.processAsync(2L, 9L);

        verify(sourceMapper, org.mockito.Mockito.never()).updateById(any(WorldSource.class));
        assertTrue(true);
    }

    @Test
    void pipelinePersistsEventWithEntityMappingAndUtcTime() {
        when(textExtractor.extract(anyString(), any())).thenReturn(
                new SourceTextExtractor.ExtractedText("全文内容", null, null));
        when(extractionProvider.extract(any())).thenReturn(new ExtractionRequest.ExtractionResult(
                List.of(new ExtractionRequest.ExtractedFact("device", "ThinkPad X1", "price", "8999.00",
                        0.97, Map.of("entity_id", "ent_1", "entity_type", "device",
                                "evidence", Map.of("text_snippet", "价格 8999")))),
                0.97,
                List.of(new ExtractionRequest.ExtractedEvent(
                        "event_1", "asset", "购买笔记本", "2026-11-15T00:00:00Z", null, null,
                        List.of("ent_1"), 0.92, "9",
                        Map.of("text_snippet", "购买日期：2026-11-15"))),
                null, Map.of(), "zh-CN", null));

        pipeline.processAsync(1L, 9L);

        ArgumentCaptor<EventCreateRequest> captor = ArgumentCaptor.forClass(EventCreateRequest.class);
        verify(eventService).create(eq(1L), captor.capture());
        EventCreateRequest created = captor.getValue();
        assertEquals(100L, created.entityId());
        assertEquals("asset", created.eventType());
        assertEquals("购买笔记本", created.title());
        assertEquals("购买日期：2026-11-15", created.description());
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), created.occurredAt());
        assertEquals(9L, created.sourceId());
    }

    @Test
    void pipelineSkipsDuplicateEvent() {
        when(textExtractor.extract(anyString(), any())).thenReturn(
                new SourceTextExtractor.ExtractedText("全文内容", null, null));
        when(eventMapper.selectCount(any())).thenReturn(1L);
        stubSingleEvent("2026-11-15T00:00:00Z");

        pipeline.processAsync(1L, 9L);

        verify(eventService, never()).create(any(), any());
    }

    @Test
    void pipelineSkipsEventWithoutAnyTime() {
        when(textExtractor.extract(anyString(), any())).thenReturn(
                new SourceTextExtractor.ExtractedText("全文内容", null, null));
        stubSingleEvent(null);

        pipeline.processAsync(1L, 9L);

        verify(eventService, never()).create(any(), any());
    }

    private void stubSingleEvent(String startAt) {
        when(extractionProvider.extract(any())).thenReturn(new ExtractionRequest.ExtractionResult(
                List.of(),
                0,
                List.of(new ExtractionRequest.ExtractedEvent(
                        "event_1", "asset", "购买笔记本", startAt, null, null,
                        List.of("ent_1"), 0.92, "9",
                        Map.of("text_snippet", "购买日期：2026-11-15"))),
                null, Map.of(), "zh-CN", null));
    }
}
