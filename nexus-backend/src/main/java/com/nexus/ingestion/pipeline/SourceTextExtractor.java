package com.nexus.ingestion.pipeline;

import com.nexus.ai.OcrProvider;
import com.nexus.ai.dto.OcrRequest;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 来源文件文本提取器（阶段四编排链路第一步）。
 * 分派规则（契约 4.1 七类型）：
 * - txt / md：直接按 UTF-8 读取全文；
 * - png / jpg / pdf：走 OCR Provider（B 实现，contentBase64 传输，本地联调友好）；
 * - docx / xlsx：走 OfficeTextExtractor（JDK 内置 ZIP+XML 轻量提取正文/单元格文本，零新增依赖）。
 */
@Component
public class SourceTextExtractor {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final Set<String> OCR_EXTENSIONS = Set.of("png", "jpg", "jpeg", "pdf");
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "xlsx");

    private final OcrProvider ocrProvider;
    private final OfficeTextExtractor officeExtractor;

    public SourceTextExtractor(OcrProvider ocrProvider, OfficeTextExtractor officeExtractor) {
        this.ocrProvider = ocrProvider;
        this.officeExtractor = officeExtractor;
    }

    /**
     * 提取全文文本。
     *
     * @param extension 小写扩展名
     * @param content   文件原始字节
     * @return 全文文本 + OCR 分块（txt/md 无分块）
     */
    public ExtractedText extract(String extension, byte[] content) {
        String ext = extension.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(ext)) {
            ext = "jpg";
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return new ExtractedText(new String(content, StandardCharsets.UTF_8), null, null);
        }
        if (OCR_EXTENSIONS.contains(ext)) {
            OcrRequest.OcrResult result = ocrProvider.recognize(new OcrRequest(
                    null, java.util.Base64.getEncoder().encodeToString(content), ext));
            return new ExtractedText(result.fullText(), result.blocks(), result.confidence());
        }
        if (OFFICE_EXTENSIONS.contains(ext)) {
            String text = "docx".equals(ext)
                    ? officeExtractor.extractDocx(content)
                    : officeExtractor.extractXlsx(content);
            return new ExtractedText(text, null, null);
        }
        throw new BizException(ResultCode.FILE_TYPE_UNSUPPORTED,
                "暂不支持 " + ext + " 类型的自动解析，请转换为 txt/md/pdf/png/jpg/docx/xlsx 后重试");
    }

    /** 提取结果：全文 + 可空 OCR 分块（用于回填 page_count） */
    public record ExtractedText(String fullText, java.util.List<OcrRequest.OcrResult.TextBlock> blocks,
                                Double ocrConfidence) {
        public int maxPage() {
            if (blocks == null || blocks.isEmpty()) {
                return 0;
            }
            return blocks.stream().mapToInt(OcrRequest.OcrResult.TextBlock::page).max().orElse(0);
        }
    }
}