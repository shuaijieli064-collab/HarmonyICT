package com.nexus.ai.dto;

import java.util.Map;

/**
 * 大模型请求/响应传输对象。
 *
 * @param templateId  提示词模板标识（模板文件由 B 维护，如 "risk-explain"）
 * @param variables   模板变量键值对
 * @param temperature 采样温度；null 表示走实现方默认配置（配置化，不写死）
 */
public record LlmRequest(String templateId, Map<String, Object> variables, Double temperature) {

    /**
     * @param content     模型输出文本
     * @param model       实际使用的模型名（留痕与评测用）
     * @param rawResponse 原始响应摘要（调试留痕用，可为 null）
     */
    public record LlmResponse(String content, String model, Map<String, Object> rawResponse) {
    }
}