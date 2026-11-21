package com.nexus.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * 信息抽取请求/结果传输对象。
 * A 只定义签名所需的数据结构；抽取字段映射以成员 B 主笔的 docs/extraction-schema.md 为准（契约整合点）。
 *
 * @param sourceText      原始文本（OCR 或文档解析后的内容）
 * @param schemaHints     抽取目标提示，如 ["assets", "liabilities", "income"]
 * @param extractedFacts  抽取出的候选事实（predicate → object 值对），实现方按 JSON Schema 校验后返回
 */
public record ExtractionRequest(String sourceText, List<String> schemaHints) {

    public record ExtractionResult(List<ExtractedFact> facts, double overallConfidence) {
    }

    /**
     * @param subjectType 主体类型（如 person/asset/event）
     * @param subjectName 主体名称
     * @param predicate   谓词（对应 world_facts.predicate）
     * @param objectValue 宾值（对应 world_facts.object_value）
     * @param confidence  置信度 0-1
     * @param extra       附加属性（时间、单位等），实现方自行定义键名并在 extraction-schema.md 登记约定
     */
    public record ExtractedFact(String subjectType, String subjectName, String predicate,
                                String objectValue, double confidence, Map<String, Object> extra) {
    }
}