package com.nexus.ai;

import com.nexus.ai.dto.OcrRequest;

/**
 * OCR 识别 Provider 接口（仅签名，实现由成员 B 完成，对接华为云 OCR）。
 * 调用约定（docs/04-代码规范.md 第 6 节）：
 * - 识别失败抛业务异常（9xxxx 段），调用方负责将 world_sources.status 置 failed；
 * - 输出全文作为 ExtractionProvider.extract 的 sourceText 输入，形成「OCR → 抽取」链路。
 */
public interface OcrProvider extends AiProvider {

    /**
     * 识别图片/PDF 中的文本。
     *
     * @param request 文件内容（OBS 对象键或 base64，二选一）
     * @return 识别结果（全文 + 分块 + 置信度）
     */
    OcrRequest.OcrResult recognize(OcrRequest request);
}