package com.nexus.agent.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 能力目录（本线独占，不修改 com.nexus.ai 的 AgentToolRegistry）。
 * 白名单是安全边界：编排器、持久化、执行器三处都以此为准，未登记动作一律拒绝落库。
 * 扩展项均为 L0 只读/建议类，绝不新增支付、删除、外部调用等动作。
 */
@Component
public class AgentCapabilityCatalog {

    /** 工具定义：level 只允许 L0/L1/L2，由构造器强制校验。 */
    public record Tool(String actionType, String level, String targetType, String description) {
        public Tool {
            if (!List.of("L0", "L1", "L2").contains(level)) {
                throw new IllegalArgumentException("非法操作级别: " + level);
            }
        }
    }

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "pay", "payment", "transfer", "withdraw", "refund", "order", "trade",
            "delete", "remove", "purge", "external", "publish", "send_message");

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public AgentCapabilityCatalog() {
        // 既有 4 项：与 com.nexus.ai 的工具表一一对应，级别不得改变
        register("read_fact", "L0", "fact", "读取有效事实，供汇总与解释");
        register("read_event", "L0", "event", "读取事件时间线，辅助汇总");
        register("create_task", "L1", "task", "创建待办，执行前需向用户展示");
        register("update_fact", "L2", "fact", "更新世界模型事实，须用户授权后执行");
        // V3 新增只读/建议类：只查不改，风险解释只能复述规则引擎结论，不产生新风险判断
        register("read_risk", "L0", "risk", "读取规则引擎已产生的风险记录");
        register("read_task", "L0", "task", "读取当前待办列表");
        register("explain_risk", "L0", "risk", "解释已有风险的触发规则与证据来源（不生成新结论）");
        register("suggest_action", "L0", "suggestion", "基于已有风险给出建议文案，不执行任何写操作");
    }

    private void register(String actionType, String level, String targetType, String description) {
        Tool tool = new Tool(actionType, level, targetType, description);
        if (isForbidden(actionType)) {
            throw new IllegalArgumentException("禁止登记高风险动作: " + actionType);
        }
        tools.put(tool.actionType(), tool);
    }

    public Optional<Tool> find(String actionType) {
        return Optional.ofNullable(tools.get(actionType));
    }

    /** 未登记动作直接拒绝，避免模型/规则绕过白名单。 */
    public Tool require(String actionType) {
        return find(actionType).orElseThrow(() -> new BizException(
                ResultCode.PARAM_INVALID, "未登记的 Agent 操作: " + actionType));
    }

    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    /** 高风险动作关键词检查，作为白名单之外的二次防线。 */
    public static boolean isForbidden(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return true;
        }
        String normalized = actionType.toLowerCase();
        return FORBIDDEN_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
