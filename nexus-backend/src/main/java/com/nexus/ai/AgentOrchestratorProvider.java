package com.nexus.ai;

import com.nexus.ai.dto.AgentPlanRequest;

/**
 * Agent 编排 Provider 接口（仅签名，实现由成员 B 完成）。
 * 规范红线：操作分级 L0（只读）/ L1（写操作，执行前展示）/ L2（写操作，须用户授权后执行）。
 * 本接口只产出"计划"，不落库、不执行；agent_action_logs 持久化与授权控制由 A 的编排服务负责。
 */
public interface AgentOrchestratorProvider extends AiProvider {

    /**
     * 根据用户意图产出候选操作计划。
     *
     * @param request 执行记录 id（A 已创建 agent_runs）+ 意图 + 上下文快照
     * @return 执行摘要与候选操作列表（含 L0-L2 分级）
     */
    AgentPlanRequest.AgentPlanResult plan(AgentPlanRequest request);
}