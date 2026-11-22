package com.nexus.ai;

import com.nexus.ai.dto.RiskAnalysisRequest;

/**
 * 风险分析 Provider 接口（仅签名，实现由成员 B 完成）。
 * 规范红线：风险结论必须由规则/算法产生（rule_code 可追溯），大模型只负责解释
 * （解释文本走 LlmProvider），禁止让模型自由生成风险判断。
 * 输出字段与 world_risks 表对齐（docs/database-design.md 第 4 节），
 * 入库由 A 的服务负责，本接口只做纯计算、不依赖数据库。
 */
public interface RiskAnalysisProvider extends AiProvider {

    /**
     * 基于用户世界模型快照执行规则引擎分析。
     *
     * @param request 当前有效事实与事件快照（A 侧查询后传入）
     * @return 候选风险列表（含 ruleCode、level、证据事实）
     */
    RiskAnalysisRequest.RiskAnalysisResult analyze(RiskAnalysisRequest request);
}