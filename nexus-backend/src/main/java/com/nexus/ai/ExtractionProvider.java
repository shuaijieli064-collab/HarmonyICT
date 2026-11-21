package com.nexus.ai;

import com.nexus.ai.dto.ExtractionRequest;

/**
 * 信息抽取 Provider 接口（仅签名，实现由成员 B 完成）。
 * 调用约定（来自 docs/04-代码规范.md 第 6 节，供 B 实现时遵守）：
 * - 结构化输出必须按 docs/extraction-schema.md 的 JSON Schema 校验后再返回；
 * - 不符合 Schema 的输出重试或标记低置信度；
 * - 每条事实必须携带 confidence，供 world_facts 入库使用。
 */
public interface ExtractionProvider extends AiProvider {

    /**
     * 从文本中抽取结构化事实。
     *
     * @param request 抽取请求（原文 + schema 提示）
     * @return 抽取结果；失败时抛业务异常（错误码 9xxxx 段），调用方负责降级
     */
    ExtractionRequest.ExtractionResult extract(ExtractionRequest request);
}