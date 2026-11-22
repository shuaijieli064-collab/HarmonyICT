package com.nexus.ai.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nexus.ai.AgentOrchestratorProvider;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.risk.RiskEvaluationContext;

/**
 * Agent 编排默认实现：意图 → 白名单工具 → 候选计划。
 * 严格只产出计划：
 * - 不落库、不执行，持久化 agent_action_logs 与授权由 A 的服务负责；
 * - 写操作只能来自 AgentToolRegistry，级别由工具定义决定，编排器不得自行降级；
 * - L1 执行前展示，L2 必须用户授权，L0 只读。
 */
@Component
public class DefaultAgentOrchestratorProvider implements AgentOrchestratorProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentOrchestratorProvider.class);
    private static final int MAX_ACTIONS = 10;

    private static final String READ_FACT = "read_fact";
    private static final String READ_EVENT = "read_event";
    private static final String CREATE_TASK = "create_task";
    private static final String UPDATE_FACT = "update_fact";

    private final AgentToolRegistry toolRegistry;
    private final AgentIntentParser intentParser;

    public DefaultAgentOrchestratorProvider(AgentToolRegistry toolRegistry, AgentIntentParser intentParser) {
        this.toolRegistry = toolRegistry;
        this.intentParser = intentParser;
    }

    @Override
    public String providerName() {
        return "agent-orchestrator-v1";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AgentPlanRequest.AgentPlanResult plan(AgentPlanRequest request) {
        if (request == null || request.intent() == null || request.intent().isBlank()) {
            return new AgentPlanRequest.AgentPlanResult("未提供可执行意图，未生成任何操作。", List.of());
        }

        AgentIntentParser.ParsedIntent parsed = intentParser.parse(request.intent(), request.facts());
        List<AgentPlanRequest.PlannedAction> actions = switch (parsed.type()) {
            case SUMMARY, UNKNOWN -> readOnlyPlan();
            case CREATE_TASK -> createTaskPlan(request, parsed);
            case UPDATE_FACT -> updateFactPlan(request, parsed);
        };

        List<AgentPlanRequest.PlannedAction> safeActions = actions.stream()
                .filter(action -> toolRegistry.find(action.actionType()).isPresent())
                .limit(MAX_ACTIONS)
                .toList();

        String summary = buildSummary(request.intent(), parsed, safeActions);
        log.debug("Agent 计划生成 runId={} intentType={} actions={}",
                request.runId(), parsed.type(), safeActions.size());
        return new AgentPlanRequest.AgentPlanResult(summary, safeActions);
    }

    private List<AgentPlanRequest.PlannedAction> readOnlyPlan() {
        Map<String, Object> factPayload = new LinkedHashMap<>();
        factPayload.put("status", "active");
        factPayload.put("limit", 20);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("limit", 20);

        List<AgentPlanRequest.PlannedAction> actions = new ArrayList<>();
        actions.add(action(READ_FACT, factPayload, "读取当前有效事实，供汇总与解释展示"));
        actions.add(action(READ_EVENT, eventPayload, "读取事件时间线，辅助汇总展示"));
        return actions;
    }

    private List<AgentPlanRequest.PlannedAction> createTaskPlan(
            AgentPlanRequest request, AgentIntentParser.ParsedIntent parsed) {
        FactSnapshot target = parsed.targetFact();
        Map<String, Object> payload = new LinkedHashMap<>();

        String title;
        String dueDate = parsed.targetDate();
        if (target != null) {
            title = "处理「" + target.subjectName() + "」的" + label(target.predicate());
            if (dueDate == null) {
                Optional<Instant> due = RiskEvaluationContext.parseInstant(target.objectValue());
                if (due.isEmpty() && target.validUntil() != null) {
                    due = Optional.of(target.validUntil());
                }
                dueDate = due.map(RiskEvaluationContext::formatDate).orElse(null);
            }
            payload.put("relatedFactId", target.id());
            payload.put("sourceType", "agent");
        } else {
            title = "待办：" + request.intent();
        }
        payload.put("title", title);
        payload.put("description", "由 Agent 根据用户意图生成：" + request.intent());
        payload.put("dueDate", dueDate);
        payload.put("priority", priority(dueDate));

        return List.of(action(CREATE_TASK, payload, "用户要求创建待办；写操作执行前需向用户展示（L1）"));
    }

    private List<AgentPlanRequest.PlannedAction> updateFactPlan(
            AgentPlanRequest request, AgentIntentParser.ParsedIntent parsed) {
        FactSnapshot target = parsed.targetFact();
        String proposedValue = parsed.requestedValue();
        if (target == null || proposedValue == null || proposedValue.isBlank()) {
            return readOnlyPlan();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("factId", target.id());
        payload.put("predicate", target.predicate());
        payload.put("currentValue", target.objectValue());
        // 字段名须与 A 的 AgentActionExecutor 一致：授权后执行器读取 objectValue
        payload.put("objectValue", proposedValue);

        return List.of(action(UPDATE_FACT, payload,
                "用户要求更新世界模型事实；写操作须用户授权后执行（L2）"));
    }

    private AgentPlanRequest.PlannedAction action(
            String actionType, Map<String, Object> payload, String reason) {
        AgentTool tool = toolRegistry.require(actionType);
        return new AgentPlanRequest.PlannedAction(
                tool.actionType(), tool.level(), tool.targetType(), null, payload, reason);
    }

    private String buildSummary(String intent, AgentIntentParser.ParsedIntent parsed,
            List<AgentPlanRequest.PlannedAction> actions) {
        long l0 = countLevel(actions, "L0");
        long l1 = countLevel(actions, "L1");
        long l2 = countLevel(actions, "L2");

        StringBuilder summary = new StringBuilder("意图「").append(intent).append("」识别为 ")
                .append(parsed.type()).append("；共生成 ").append(actions.size())
                .append(" 个操作（L0 ").append(l0).append(" / L1 ").append(l1)
                .append(" / L2 ").append(l2).append("）");
        if (l1 > 0) {
            summary.append("；L1 操作执行前需向用户展示");
        }
        if (l2 > 0) {
            summary.append("；L2 操作需用户授权后执行");
        }
        if (parsed.type() == AgentIntentParser.IntentType.UPDATE_FACT && parsed.targetFact() == null) {
            summary.append("；未定位到唯一事实或缺少新值，已降级为只读计划，请补充事实名称/ID 与新值");
        }
        return summary.toString();
    }

    private long countLevel(List<AgentPlanRequest.PlannedAction> actions, String level) {
        return actions.stream().filter(action -> level.equals(action.level())).count();
    }

    private int priority(String dueDate) {
        if (dueDate == null) {
            return 0;
        }
        try {
            long days = ChronoUnit.DAYS.between(
                    LocalDate.now(ZoneOffset.UTC), LocalDate.parse(dueDate));
            return days <= 7 ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String label(String predicate) {
        return switch (predicate) {
            case "warranty_until" -> "保修到期";
            case "subscription_renewal_at" -> "订阅续费";
            case "subscription_end_at" -> "订阅到期";
            case "task_due_at" -> "任务截止";
            case "goal_deadline" -> "目标截止";
            case "project_deadline" -> "项目截止";
            case "price" -> "价格核对";
            default -> predicate;
        };
    }
}