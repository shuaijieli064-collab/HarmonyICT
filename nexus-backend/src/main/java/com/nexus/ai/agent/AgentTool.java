package com.nexus.ai.agent;

/**
 * Agent 可执行工具定义。级别只能是 L0/L1/L2：
 * L0 只读、L1 写操作执行前展示、L2 写操作须用户授权。
 * 工具是白名单，编排器不得生成未注册的 actionType。
 */
public record AgentTool(String actionType, String level, String targetType, String description) {

    public AgentTool {
        if (!"L0".equals(level) && !"L1".equals(level) && !"L2".equals(level)) {
            throw new IllegalArgumentException("非法操作级别: " + level);
        }
    }
}