package com.nexus.ingestion.pipeline;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Office 轻量解析器验证：docx 正文 / xlsx 共享字符串与数值单元格 / XXE 防护 / 损坏文件。
 */
class OfficeTextExtractorTest {

    private final OfficeTextExtractor extractor = new OfficeTextExtractor();

    @Test
    void extractsDocxParagraphText() {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>笔记本发票</w:t></w:r><w:r><w:t>6999 元</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """;
        byte[] docx = zipOf(Map.of("word/document.xml", documentXml));
        String text = extractor.extractDocx(docx);
        assertTrue(text.contains("笔记本发票"));
        assertTrue(text.contains("6999 元"));
    }

    @Test
    void extractsXlsxSharedStringsAndNumbers() {
        String shared = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>订阅服务</t></si><si><t>年费</t></si>
                </sst>
                """;
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                    <row r="2"><c r="A2" t="inlineStr"><is><t>网盘</t></is></c><c r="B2"><v>365</v></c></row>
                  </sheetData>
                </worksheet>
                """;
        byte[] xlsx = zipOf(Map.of("xl/sharedStrings.xml", shared, "xl/worksheets/sheet1.xml", sheet));
        String text = extractor.extractXlsx(xlsx);
        assertTrue(text.contains("订阅服务"), text);
        assertTrue(text.contains("年费"), text);
        assertTrue(text.contains("网盘"), text);
        assertTrue(text.contains("365"), text);
    }

    @Test
    void rejectsDtdToPreventXxe() {
        String malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body>
                </w:document>
                """;
        byte[] docx = zipOf(Map.of("word/document.xml", malicious));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractDocx(docx));
    }

    @Test
    void rejectsCorruptedZip() {
        assertThrows(IllegalArgumentException.class,
                () -> extractor.extractDocx("not a zip".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sourceTextExtractorRoutesOfficeExtensions() {
        OcrProviderStub ocr = new OcrProviderStub();
        SourceTextExtractor textExtractor = new SourceTextExtractor(ocr, extractor);
        byte[] docx = zipOf(Map.of("word/document.xml",
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body><w:p><w:r><w:t>合同正文</w:t></w:r></w:p></w:body></w:document>"));
        SourceTextExtractor.ExtractedText result = textExtractor.extract("DOCX", docx);
        assertTrue(result.fullText().contains("合同正文"));
        assertEquals(0, ocr.calls);
    }

    private static byte[] zipOf(Map<String, String> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** 极简 OCR 桩：路由测试中断言其未被调用（docx 不应走 OCR） */
    private static class OcrProviderStub implements com.nexus.ai.OcrProvider {
        int calls = 0;

        @Override
        public String providerName() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public com.nexus.ai.dto.OcrRequest.OcrResult recognize(com.nexus.ai.dto.OcrRequest request) {
            calls++;
            throw new AssertionError("docx 不应走 OCR 链路");
        }
    }
}
