package com.nexus.ai.dto;

import java.util.List;

/**
 * OCR 请求/结果传输对象。
 * A 只定义签名所需的数据结构；识别分块与置信度细节由 B 在联调时登记到 docs/extraction-schema.md。
 *
 * @param obsKey        OBS 对象键（阶段二接 OBS 后优先使用；与 contentBase64 二选一）
 * @param contentBase64 文件内容 base64（本地联调用）
 * @param mimeType      文件类型：png / jpg / pdf
 */
public record OcrRequest(String obsKey, String contentBase64, String mimeType) {

    /**
     * @param fullText   全文文本（按阅读顺序拼接，供信息抽取使用）
     * @param blocks     分块文本（含页码，供前端高亮原文出处）
     * @param confidence 整体识别置信度 0-1
     */
    public record OcrResult(String fullText, List<TextBlock> blocks, double confidence) {

        /**
         * @param page       页码（从 1 开始）
         * @param text       块文本
         * @param confidence 块级置信度 0-1
         */
        public record TextBlock(int page, String text, double confidence) {
        }
    }
}