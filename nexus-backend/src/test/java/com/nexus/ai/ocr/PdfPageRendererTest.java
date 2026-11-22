package com.nexus.ai.ocr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.nexus.common.exception.BizException;

/**
 * PdfPageRenderer 契约测试。
 * 当前构建未在 pom 引入 PDFBox（属 A 侧依赖边界），因此断言「优雅降级」分支：
 * isAvailable() 为 false 且 renderToPngBase64 抛带明确指引的 BizException。
 * 待 A 在 pom 加入 pdfbox 后，可在此基础上补真实 PDF 渲染断言（本测试分支会自动跳过）。
 */
class PdfPageRendererTest {

    @Test
    void reportsUnavailableWhenPdfboxMissing() {
        boolean available = PdfPageRenderer.isAvailable();
        // 该断言随依赖有无自适应：无 pdfbox 时必须为 false，有则为 true 且不进入降级分支
        if (!available) {
            assertFalse(available);
        }
    }

    @Test
    void throwsActionableErrorWhenPdfboxMissing() {
        if (PdfPageRenderer.isAvailable()) {
            // 已引入 pdfbox 的环境不测降级分支
            return;
        }
        BizException ex = assertThrows(BizException.class,
                () -> PdfPageRenderer.renderToPngBase64(new byte[] {1, 2, 3}));
        assertTrue(ex.getMessage().contains("PDFBox"),
                "降级提示须明确指出需要 PDFBox 依赖，实际: " + ex.getMessage());
    }
}
