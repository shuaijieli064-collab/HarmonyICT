package com.nexus.ai;

import com.nexus.ai.dto.LlmRequest;

/**
 * 大模型推理 Provider 接口（仅签名，实现由成员 B 完成，对接华为云 MaaS）。
 * 规范红线（docs/04-代码规范.md 第 6 节）：
 * - 提示词集中管理为模板文件，禁止散落在代码中，模板经 templateId 引用；
 * - 模型、温度等关键参数走配置；
 * - 禁止由本接口直接产生风险结论，模型输出仅做解释与文本加工。
 */
public interface LlmProvider extends AiProvider {

    /** 按模板渲染提示词并调用大模型补全 */
    LlmRequest.LlmResponse complete(LlmRequest request);
}