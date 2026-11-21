package com.nexus.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.LlmProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.ExtractionRequest;
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
}
