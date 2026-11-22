package com.nexus.ai.mock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nexus.ai.OcrProvider;
import com.nexus.ai.dto.OcrRequest;

/**
 * 本地 mock OCR。纯文本 base64 直接解码，便于跑通 TXT 链路；
 * 图片不做伪造识别，返回空文本并告警，图片验收必须切到 real 模式。
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai", name = "mode", havingValue = "mock")
public class MockOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(MockOcrProvider.class);

    @Override
    public String providerName() {
        return "mock-ocr";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public OcrRequest.OcrResult recognize(OcrRequest request) {
        String mimeType = request.mimeType() == null ? "" : request.mimeType().toLowerCase();
        if (mimeType.startsWith("text/") && request.contentBase64() != null) {
            String text = new String(Base64.getDecoder().decode(request.contentBase64()), StandardCharsets.UTF_8);
            return new OcrRequest.OcrResult(text,
                    List.of(new OcrRequest.OcrResult.TextBlock(1, text, 1.0)), 1.0);
        }
        log.warn("mock OCR 不识别图片内容 mimeType={}，请使用 real 模式验收", mimeType);
        return new OcrRequest.OcrResult("", List.of(), 0);
    }
}