package com.nexus.agent.service.impl;

import com.nexus.ai.dto.AgentPlanRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V3 只读/建议工具扩展：在 B 的编排结果之后追加 L0 动作，不改 com.nexus.ai 的编排器。
 * 为什么只追加 L0：写动作的级别、授权与执行链路仍由原编排器和执行器掌控，扩展不引入新的写面。
 * 仅在用户明确表达「查看/解释/建议」类意图时追加，避免影响既有汇总与待办创建流程。
 */
@Component
public class AgentPlanAugmenter {

    private static final int MAX_ACTIONS = 10;

    private static final Pattern QUERY_VERB = Pattern.compile("查看|查询|列出|有哪些|有没有|解释|说明|分析|为什么");
    private static final Pattern RISK_WORD = Pattern.compile("风险|到期|过期|冲突|预警", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_WORD = Pattern.compile("待办|任务", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADVICE_WORD = Pattern.compile("建议|推荐", Pattern.CASE_INSENSITIVE);
    private static final Pattern RISK_EN = Pattern.compile("\\brisk|expiry|conflict\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_EN = Pattern.compile("\\btask|todo\\b", Pattern.CASE_INSENSITIVE);

    /** 在既有计划后追加 L0 只读工具；返回新列表，不修改入参。 */
    public List<AgentPlanRequest.PlannedAction> augment(String intent, List<AgentPlanRequest.PlannedAction> base) {
        List<AgentPlanRequest.PlannedAction> actions = new ArrayList<>(
                base == null ? List.of() : base);
        if (intent == null || intent.isBlank()) {
            return actions;
        }
        String text = intent.toLowerCase(Locale.ROOT);
        boolean querying = QUERY_VERB.matcher(text).find();

        if (querying && (RISK_WORD.matcher(text).find() || RISK_EN.matcher(text).find())) {
            addOnce(actions, readonly("read_risk", "risk", "读取规则引擎已产生的风险记录"));
            addOnce(actions, readonly("explain_risk", "risk",
                    "解释已有风险的触发规则与证据，不生成新风险结论"));
        }
        if (querying && (TASK_WORD.matcher(text).find() || TASK_EN.matcher(text).find())) {
            addOnce(actions, readonly("read_task", "task", "读取当前待办列表与状态"));
        }
        if (ADVICE_WORD.matcher(text).find()) {
            addOnce(actions, readonly("suggest_action", "suggestion",
                    "基于已有风险给出建议文案，不执行任何写操作"));
        }
        return actions.stream().limit(MAX_ACTIONS).toList();
    }

    private static AgentPlanRequest.PlannedAction readonly(
            String actionType, String targetType, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", 20);
        return new AgentPlanRequest.PlannedAction(actionType, "L0", targetType, null, payload, reason);
    }

    private static void addOnce(List<AgentPlanRequest.PlannedAction> actions,
                                AgentPlanRequest.PlannedAction candidate) {
        boolean exists = actions.stream().anyMatch(a -> candidate.actionType().equals(a.actionType()));
        if (!exists) {
            actions.add(candidate);
        }
    }
}
