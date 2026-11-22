package com.nexus.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * Agent 编排请求/结果传输对象。
 *
 * @param runId  agent_runs.id（A 已创建执行记录后传入）
 * @param intent 用户意图原文（对应 agent_runs.intent）
 * @param facts  上下文事实快照
 * @param events 上下文事件快照
 */
public record AgentPlanRequest(Long runId, String intent, List<FactSnapshot> facts, List<EventSnapshot> events) {

    /**
     * @param summary 执行摘要（结束后由 A 写入 agent_runs.summary）
     * @param actions 候选操作列表，由 A 持久化为 agent_action_logs 并控制授权
     */
    public record AgentPlanResult(String summary, List<PlannedAction> actions) {
    }

    /**
     * @param actionType create_task / update_fact / send_notification / read_fact 等
     * @param level      L0（只读）/ L1（写操作，执行前展示）/ L2（写操作，须用户授权）
     * @param targetType 操作目标类型：task / fact / entity 等
     * @param targetId   目标对象 id（新建类操作为 null）
     * @param payload    操作参数（对应 agent_action_logs.payload，JSONB）
     * @param reason     执行理由（展示用，可由模型生成，不作为风险依据）
     */
    public record PlannedAction(String actionType, String level, String targetType, Long targetId,
                                Map<String, Object> payload, String reason) {
    }
}