package com.nexus.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.util.AgentAuditSupport;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.nexus.task.dto.TaskCreateRequest;
import com.nexus.task.dto.TaskResponse;
import com.nexus.task.service.TaskService;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.dto.FactUpdateRequest;
import com.nexus.world.service.WorldFactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Agent 已授权动作执行器。
 * 解决 B review 反馈的缺口：authorize(approve=true) 之后只改状态不真正执行，pending 动作永久挂起。
 * 现在授权后立即逐条执行：create_task 真实落库任务、update_fact 走事实链式失效，单条失败标 failure 不影响其他动作。
 * 未登记的 actionType 保持 pending（不臆造语义），待登记执行逻辑。
 */
@Component
public class AgentActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentActionExecutor.class);

    private final AgentActionLogMapper logMapper;
    private final TaskService taskService;
    private final WorldFactService factService;
    private final ObjectMapper objectMapper;

    public AgentActionExecutor(AgentActionLogMapper logMapper, TaskService taskService,
                               WorldFactService factService, ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.taskService = taskService;
        this.factService = factService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行指定 run 下已授权（authorized=true）且待执行的（result=pending）动作。
     *
     * @return 实际执行成功数
     */
    @Transactional
    public int executeAuthorized(Long userId, Long runId) {
        List<AgentActionLog> pending = logMapper.selectList(new LambdaQueryWrapper<AgentActionLog>()
                .eq(AgentActionLog::getUserId, userId)
                .eq(AgentActionLog::getRunId, runId)
                .eq(AgentActionLog::getAuthorized, true)
                .eq(AgentActionLog::getResult, "pending"));
        int success = 0;
        for (AgentActionLog actionLog : pending) {
            try {
                executeOne(userId, actionLog);
                success++;
            } catch (Exception e) {
                // 单条失败不影响其余动作；失败原因随 audit 段落库，供前端时间线展示
                log.warn("动作执行失败: logId={}, type={}, reason={}",
                        actionLog.getId(), actionLog.getActionType(), e.getMessage());
                actionLog.setPayload(AgentAuditSupport.write(actionLog.getPayload(),
                        "failureReason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                markResult(actionLog, "failure");
            }
        }
        return success;
    }

    private void executeOne(Long userId, AgentActionLog actionLog) {
        // 执行路径再次校验白名单与级别，防止历史数据或绕过编排器写入的危险动作被执行
        String actionType = actionLog.getActionType();
        if (AgentCapabilityCatalog.isForbidden(actionType)) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "禁止执行高风险动作: " + actionType);
        }
        String level = actionLog.getLevel();
        if (level != null && !List.of("L0", "L1", "L2").contains(level)) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "非法操作级别: " + level);
        }
        Map<String, Object> payload = parsePayload(actionLog.getPayload());
        switch (actionLog.getActionType() == null ? "" : actionLog.getActionType()) {
            case "create_task" -> {
                TaskResponse task = taskService.create(userId, toTaskRequest(payload));
                actionLog.setTargetType("task");
                actionLog.setTargetId(task.id());
                markResult(actionLog, "success");
                log.info("Agent 动作 create_task 执行成功: runId={}, taskId={}", actionLog.getRunId(), task.id());
            }
            case "update_fact" -> {
                Long factId = requireLong(payload, "factId");
                FactResponse fact = factService.update(userId, factId,
                        new FactUpdateRequest(requireString(payload, "objectValue")));
                actionLog.setTargetType("fact");
                actionLog.setTargetId(fact.id());
                markResult(actionLog, "success");
                log.info("Agent 动作 update_fact 执行成功: runId={}, newFactId={}", actionLog.getRunId(), fact.id());
            }
            default -> // 未登记类型不臆造执行语义，保持 pending 等待登记
                log.debug("未登记的 Agent 动作类型，保持 pending: {}", actionLog.getActionType());
        }
    }

    private TaskCreateRequest toTaskRequest(Map<String, Object> payload) {
        String title = requireString(payload, "title");
        String description = optionalString(payload, "description");
        Integer priority = payload.get("priority") == null ? null
                : Integer.valueOf(String.valueOf(payload.get("priority")));
        LocalDate dueDate = payload.get("dueDate") == null ? null
                : LocalDate.parse(String.valueOf(payload.get("dueDate")));
        // Agent 派生的任务统一 sourceType=agent，保证行动项来源可审计；
        // relatedFactId 为 B 编排器 payload 中的来源事实 id，执行器透传落库（PR #2 review 拍板）
        return new TaskCreateRequest(title, description, priority, dueDate, "agent", null,
                payload.get("relatedFactId") == null ? null
                        : Long.valueOf(String.valueOf(payload.get("relatedFactId"))));
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException(com.nexus.common.result.ResultCode.PARAM_INVALID, "动作 payload 不是合法 JSON");
        }
    }

    private Long requireLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new BizException(com.nexus.common.result.ResultCode.PARAM_INVALID, "payload 缺少 " + key);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String requireString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null || value.isBlank()) {
            throw new BizException(com.nexus.common.result.ResultCode.PARAM_INVALID, "payload 缺少 " + key);
        }
        return value;
    }

    private String optionalString(Map<String, Object> payload, String key) {
        return payload.get(key) == null ? null : String.valueOf(payload.get(key));
    }

    private void markResult(AgentActionLog actionLog, String result) {
        Instant now = Instant.now();
        actionLog.setResult(result);
        actionLog.setUpdatedAt(now);
        actionLog.setPayload(AgentAuditSupport.write(actionLog.getPayload(), Map.of(
                "executedAt", now.toString(),
                "executionResult", result)));
        logMapper.updateById(actionLog);
    }
}