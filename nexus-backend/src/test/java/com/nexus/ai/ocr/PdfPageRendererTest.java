package com.nexus.ai.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import com.nexus.common.exception.BizException;

/**
 * PdfPageRenderer 契约测试。
 *
 * PR #6 已由 A 在 pom 引入 PDFBox 2.0.31，因此这里能直接构造真实 PDF 验证逐页渲染与页数截断；
 * 若依赖被移除（反射降级场景），真实渲染用例自动跳过，仅保留降级分支断言。
 */
class PdfPageRendererTest {

    @Test
    void trimsRenderPageCountToMaxPages() {
        // 纯函数边界：0/负数不渲染；未超限原样；超限截断到 MAX_PAGES
        assertEquals(0, PdfPageRenderer.resolveRenderPageCount(0));
        assertEquals(0, PdfPageRenderer.resolveRenderPageCount(-3));
        assertEquals(1, PdfPageRenderer.resolveRenderPageCount(1));
        assertEquals(PdfPageRenderer.MAX_PAGES, PdfPageRenderer.resolveRenderPageCount(PdfPageRenderer.MAX_PAGES));
        assertEquals(PdfPageRenderer.MAX_PAGES, PdfPageRenderer.resolveRenderPageCount(PdfPageRenderer.MAX_PAGES + 1));
        assertEquals(PdfPageRenderer.MAX_PAGES, PdfPageRenderer.resolveRenderPageCount(500));
    }

    @Test
    void rendersEveryPageWhenWithinLimit() throws Exception {
        assumeTrue(PdfPageRenderer.isAvailable(), "未引入 PDFBox 时跳过真实渲染断言");

        List<String> pages = PdfPageRenderer.renderToPngBase64(blankPdf(3));

        assertEquals(3, pages.size(), "3 页 PDF 应产出 3 张图片");
        for (String page : pages) {
            assertTrue(isPng(page), "每页输出应为 PNG 的 base64");
        }
    }

    @Test
    void truncatesOversizedPdfToMaxPages() throws Exception {
        assumeTrue(PdfPageRenderer.isAvailable(), "未引入 PDFBox 时跳过真实渲染断言");

        int totalPages = PdfPageRenderer.MAX_PAGES + 5;
        List<String> pages = PdfPageRenderer.renderToPngBase64(blankPdf(totalPages));

        assertEquals(PdfPageRenderer.MAX_PAGES, pages.size(),
                "超过上限的 PDF 只渲染前 " + PdfPageRenderer.MAX_PAGES + " 页，防止 OOM");
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

    @Test
    void throwsBizExceptionOnCorruptedPdf() {
        assumeTrue(PdfPageRenderer.isAvailable(), "未引入 PDFBox 时走降级分支，由上一用例覆盖");

        BizException ex = assertThrows(BizException.class,
                () -> PdfPageRenderer.renderToPngBase64("not a pdf".getBytes()));
        assertTrue(ex.getMessage().contains("PDF 转图失败"), "损坏 PDF 应转为业务异常，实际: " + ex.getMessage());
    }

    /** 构造 n 页空白 PDF（无内容流，渲染结果仍为合法 PNG）。 */
    private static byte[] blankPdf(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static boolean isPng(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return bytes.length > 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }
}
