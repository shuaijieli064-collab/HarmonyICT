package com.nexus.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * 风险分析请求/结果传输对象。
 *
 * @param facts      当前有效事实快照（status=active 的 world_facts）
 * @param events     相关事件快照（期限、冲突类规则使用）
 * @param analysisAt 分析基准时间（UTC，由调用方传入，规则按此计算剩余天数）
 */
public record RiskAnalysisRequest(List<FactSnapshot> facts, List<EventSnapshot> events, Instant analysisAt) {

    /**
     * @param risks 候选风险列表，由 A 侧服务入库 world_risks
     */
    public record RiskAnalysisResult(List<DetectedRisk> risks) {
    }

    /**
     * @param riskType        cert_expiry / contract_deadline / asset_concentration / data_staleness / other
     * @param level           low / medium / high
     * @param ruleCode        规则标识（如 CERT_EXPIRY_30D），保证风险可追溯（world_risks.rule_code）
     * @param title           风险标题（world_risks.title）
     * @param description     触发原因与证据说明；必须由规则产出，模型解释另行经 LlmProvider 生成
     * @param entityId        关联实体 id（可为 null）
     * @param evidenceFactIds 证据事实 id 列表（world_facts.id）
     */
    public record DetectedRisk(String riskType, String level, String ruleCode, String title,
                               String description, Long entityId, List<Long> evidenceFactIds) {
    }
}