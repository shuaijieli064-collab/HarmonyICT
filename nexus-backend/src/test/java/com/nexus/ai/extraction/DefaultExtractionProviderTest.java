package com.nexus.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.LlmProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ai.dto.LlmRequest;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * DefaultExtractionProvider 的 sourceId 契约测试（PR#1 拍板：禁止自造 UUID，缺 sourceId 抛 90011）。
 */
class DefaultExtractionProviderTest {

    private final LlmProvider llmProvider = mock(LlmProvider.class);
    private final AiProperties properties = new AiProperties();
    private final ExtractionOutputParser parser = new ExtractionOutputParser(new ObjectMapper());

    private DefaultExtractionProvider provider() {
        return new DefaultExtractionProvider(llmProvider, parser, properties);
    }

    @Test
    void extract_withoutSourceId_throws90011() {
        ExtractionRequest request = new ExtractionRequest(null, "任意原文", null);

        BizException ex = assertThrows(BizException.class, () -> provider().extract(request));

        assertEquals(ResultCode.EXTRACTION_NO_SOURCE, ex.getResultCode());
        assertEquals(90011, ex.getResultCode().getCode());
    }

    @Test
    void extract_withBlankSourceId_throws90011() {
        ExtractionRequest request = new ExtractionRequest("   ", "任意原文", null);

        BizException ex = assertThrows(BizException.class, () -> provider().extract(request));

        assertEquals(ResultCode.EXTRACTION_NO_SOURCE, ex.getResultCode());
    }

    @Test
    void extract_withoutSourceText_throwsParamInvalid() {
        ExtractionRequest request = new ExtractionRequest("1948123456789012345", "", null);

        BizException ex = assertThrows(BizException.class, () -> provider().extract(request));

        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
    }

    @Test
    void extract_withUnregisteredPredicate_throws90013() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "测试资产" } ],
                  "facts": [
                    {
                      "subjectRef": "ent_1",
                      "predicate": "not_registered_predicate",
                      "objectValue": "x",
                      "valueType": "string",
                      "confidence": 0.90,
                      "factStatus": "confirmed",
                      "evidence": { "textSnippet": "原文证据" }
                    }
                  ]
                }
                """;
        when(llmProvider.complete(any(LlmRequest.class)))
                .thenReturn(new LlmRequest.LlmResponse(json, "mock-model", null));

        BizException ex = assertThrows(BizException.class,
                () -> provider().extract(new ExtractionRequest("1948123456789012345", "任意原文", null)));

        assertEquals(ResultCode.EXTRACTION_PREDICATE, ex.getResultCode());
        assertEquals(90013, ex.getResultCode().getCode());
    }
}
