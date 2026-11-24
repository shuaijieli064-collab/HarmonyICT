package com.nexus.ai.agent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * Agent 工具注册表。为什么用白名单：模型/规则只能从注册表选择动作，
 * 避免出现删除、转账等未被产品与安全评审允许的操作。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public AgentToolRegistry() {
        register(new AgentTool("read_fact", "L0", "fact", "读取有效事实，供汇总与解释"));
        register(new AgentTool("read_event", "L0", "event", "读取事件时间线，辅助汇总"));
        register(new AgentTool("read_risk", "L0", "risk", "读取当前风险列表，辅助解释与建议"));
        register(new AgentTool("create_task", "L1", "task", "创建待办，执行前需向用户展示"));
        register(new AgentTool("update_fact", "L2", "fact", "更新世界模型事实，须用户授权后执行"));
        register(new AgentTool("create_event", "L2", "event", "创建日程/事件（课程、会议、评审等），须用户授权后执行"));
    }

    private void register(AgentTool tool) {
        tools.put(tool.actionType(), tool);
    }

    public Optional<AgentTool> find(String actionType) {
        return Optional.ofNullable(tools.get(actionType));
    }

    public AgentTool require(String actionType) {
        return find(actionType)
                .orElseThrow(() -> new BizException(ResultCode.PARAM_INVALID, "未注册的 Agent 操作: " + actionType));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }
}