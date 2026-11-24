package com.nexus.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V3 新增只读/建议工具的实际执行逻辑。
 * 红线：
 * 1. 所有查询都带 user_id；风险证据先经用户维度风险 id 过滤再查关联表；
 * 2. 只调用 selectList，没有任何 insert/update/delete；
 * 3. explain_risk / suggest_action 只复述规则引擎已产生的 ruleCode 与描述，不生成新风险结论。
 */
@Component
public class AgentReadOnlyToolService {

    private static final Set<String> SUPPORTED = Set.of(
            "read_risk", "read_task", "explain_risk", "suggest_action");

    private final WorldRiskMapper riskMapper;
    private final WorldRiskFactMapper riskFactMapper;
    private final TaskMapper taskMapper;

    public AgentReadOnlyToolService(WorldRiskMapper riskMapper, WorldRiskFactMapper riskFactMapper,
                                    TaskMapper taskMapper) {
        this.riskMapper = riskMapper;
        this.riskFactMapper = riskFactMapper;
        this.taskMapper = taskMapper;
    }

    public boolean supports(String actionType) {
        return SUPPORTED.contains(actionType);
    }

    /** 执行只读工具，返回可写入审计段的摘要信息；未登记类型返回空。 */
    public Map<String, Object> execute(Long userId, String actionType, Map<String, Object> payload) {
        if (!supports(actionType)) {
            return Map.of();
        }
        return switch (actionType) {
            case "read_risk" -> readRisk(userId, payload);
            case "read_task" -> readTask(userId, payload);
            case "explain_risk" -> explainRisk(userId);
            case "suggest_action" -> suggestAction(userId, payload);
            default -> Map.of();
        };
    }

    private Map<String, Object> readRisk(Long userId, Map<String, Object> payload) {
        List<WorldRisk> risks = safe(riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .orderByDesc(WorldRisk::getCreatedAt)
                .last("LIMIT " + limit(payload))));
        List<Map<String, Object>> items = new ArrayList<>();
        for (WorldRisk risk : risks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(risk.getId()));
            item.put("riskType", risk.getRiskType());
            item.put("level", risk.getLevel());
            item.put("status", risk.getStatus());
            item.put("ruleCode", risk.getRuleCode());
            item.put("title", risk.getTitle());
            items.add(item);
        }
        return Map.of("tool", "read_risk", "count", items.size(), "items", items);
    }

    private Map<String, Object> readTask(Long userId, Map<String, Object> payload) {
        List<Task> tasks = safe(taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .orderByAsc(Task::getDueDate)
                .last("LIMIT " + limit(payload))));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(task.getId()));
            item.put("title", task.getTitle());
            item.put("status", task.getStatus());
            item.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
            items.add(item);
        }
        return Map.of("tool", "read_task", "count", items.size(), "items", items);
    }

    /** 只解释已存在的风险：文本来自规则引擎落库的 description，证据 id 来自关联表。 */
    private Map<String, Object> explainRisk(Long userId) {
        List<WorldRisk> risks = safe(riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, "open")
                .orderByDesc(WorldRisk::getCreatedAt)
                .last("LIMIT 1")));
        if (risks.isEmpty()) {
            return Map.of("tool", "explain_risk", "count", 0,
                    "explanation", "当前没有待解释的开放风险");
        }
        WorldRisk risk = risks.get(0);
        List<WorldRiskFact> evidence = safe(riskFactMapper.selectList(
                new LambdaQueryWrapper<WorldRiskFact>().eq(WorldRiskFact::getRiskId, risk.getId())));
        List<String> evidenceIds = evidence.stream()
                .map(e -> String.valueOf(e.getFactId())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "explain_risk");
        result.put("count", 1);
        result.put("riskId", String.valueOf(risk.getId()));
        result.put("ruleCode", risk.getRuleCode());
        result.put("explanation", risk.getDescription() == null ? risk.getTitle() : risk.getDescription());
        result.put("source", "规则引擎（模型仅解释，不产生风险结论）");
        result.put("evidenceFactIds", evidenceIds);
        return result;
    }

    /** 建议文案由规则引擎已落库的风险标题派生，绝不触发写操作。 */
    private Map<String, Object> suggestAction(Long userId, Map<String, Object> payload) {
        List<WorldRisk> risks = safe(riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, "open")
                .orderByDesc(WorldRisk::getCreatedAt)
                .last("LIMIT " + limit(payload))));
        List<String> suggestions = new ArrayList<>();
        for (WorldRisk risk : risks) {
            suggestions.add("建议为已识别风险「" + risk.getTitle() + "」创建待办（需用户确认）");
        }
        return Map.of("tool", "suggest_action", "count", suggestions.size(),
                "suggestions", suggestions, "writesPerformed", false);
    }

    private static int limit(Map<String, Object> payload) {
        if (payload == null || payload.get("limit") == null) {
            return 20;
        }
        try {
            int value = Integer.parseInt(String.valueOf(payload.get("limit")));
            return Math.max(1, Math.min(value, 20));
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    private static <T> List<T> safe(List<T> source) {
        return source == null ? List.of() : source;
    }
}
