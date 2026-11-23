package com.nexus.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * 信息抽取请求/结果传输对象。
 * A 只定义签名所需的数据结构；抽取字段映射以成员 B 主笔的 docs/extraction-schema.md 为准（契约整合点）。
 * sourceId 契约（2026-11-22）：必须使用 A 侧上传接口返回的 world_sources.id（雪花数字字符串），
 * B 只做透传，禁止自造 UUID —— 见 PR #1 review 阻塞项 1 的拍板结论。
 *
 * @param sourceId        来源资料 ID（A 上传接口返回的雪花数字字符串，B 透传）
 * @param sourceText      原始文本（OCR 或文档解析后的内容）
 * @param schemaHints     抽取目标提示，如 ["assets", "liabilities", "income"]
 * @param extractedFacts  抽取出的候选事实（predicate → object 值对），实现方按 JSON Schema 校验后返回
 */
public record ExtractionRequest(String sourceId, String sourceText, List<String> schemaHints) {

    /**
     * 抽取结果。events 与四个顶层字段按 2026-11-23 events 接入拍板补齐；
     * 两参数构造器保留，供既有单元测试与人工构造场景使用（events 默认为空列表）。
     *
     * @param extractionId 单次抽取任务 UUID，用于重试追踪与日志（不落库）
     * @param source       来源对象快照（不落库）
     * @param language     BCP 47 语言标签（不落库）
     * @param extractedAt  抽取完成时间，ISO 8601 UTC 字符串（不落库）
     */
    public record ExtractionResult(List<ExtractedFact> facts, double overallConfidence,
                                   List<ExtractedEvent> events, String extractionId,
                                   Map<String, Object> source, String language, String extractedAt) {

        public ExtractionResult(List<ExtractedFact> facts, double overallConfidence) {
            this(facts, overallConfidence, List.of(), null, null, null, null);
        }
    }

    /**
     * @param subjectType 主体类型（如 person/asset/event）
     * @param subjectName 主体名称
     * @param predicate   谓词（对应 world_facts.predicate）
     * @param objectValue 宾值（对应 world_facts.object_value）
     * @param confidence  置信度 0-1
     * @param extra       附加属性（时间、单位等），snake_case 为规范键，camelCase 兼容读取
     */
    public record ExtractedFact(String subjectType, String subjectName, String predicate,
                                String objectValue, double confidence, Map<String, Object> extra) {
    }

    /**
     * 抽取事件（extraction-schema.md 第 4.4 节）。时间字段保持 ISO 8601 字符串，
     * Parser 已完成 +08:00 归一；入库时由编排服务按 start_at > due_at > end_at 选取 occurred_at。
     *
     * @param eventId           文档内临时事件 ID（ent/event_N），不落库
     * @param eventType         schema 10 值枚举，Parser 已映射为 world_events 的 6 值
     * @param title             事件标题（必填）
     * @param startAt           开始时间，可为 null
     * @param endAt             结束时间，可为 null
     * @param dueAt             截止/续费时间，可为 null
     * @param relatedEntityRefs 关联实体引用（ent_N），可为 null
     * @param confidence        置信度 0-1，仅作入库门槛
     * @param sourceId          A 上传接口返回的雪花 ID 字符串（B 原样透传）
     * @param evidence          原文证据（text_snippet / page），必填
     */
    public record ExtractedEvent(String eventId, String eventType, String title,
                                 String startAt, String endAt, String dueAt,
                                 List<String> relatedEntityRefs, double confidence,
                                 String sourceId, Map<String, Object> evidence) {
    }
}