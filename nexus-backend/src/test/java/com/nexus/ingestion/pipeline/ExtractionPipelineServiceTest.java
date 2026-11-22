package com.nexus.ingestion.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.ExtractionProvider;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ai.dto.OcrRequest;
import com.nexus.ingestion.storage.StorageProvider;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.world.service.WorldEntityService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段四编排链路验证：上传 → 异步抽取 → 实体 find-or-create → 事实落库（confidence/factStatus 透传）→ 状态机。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtractionPipelineServiceTest {

    @Mock
    private WorldSourceMapper sourceMapper;

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

    private ExtractionPipelineService pipeline;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        pipeline = new ExtractionPipelineService(sourceMapper, storageProvider, textExtractor,
                extractionProvider, entityService, factService, objectMapper);
        when(sourceMapper.selectById(9L)).thenReturn(pendingSource());
        when(sourceMapper.updateById(any(WorldSource.class))).thenReturn(1);
        when(storageProvider.load(1L, "key-9")).thenReturn("stub".getBytes(StandardCharsets.UTF_8));
        when(entityService.findOrCreateByNormalizedName(any(), anyString(), anyString(), anyString()))
                .thenReturn(100L);
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
}