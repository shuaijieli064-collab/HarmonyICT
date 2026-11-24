package com.nexus.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.agent.util.AgentAuditSupport;
import com.nexus.ai.AgentOrchestratorProvider;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 异步编排执行器。
 * 职责：组装用户维度上下文 → 调用 AgentOrchestratorProvider.plan（B 实现）→
 * V3 追加 L0 只读/建议工具 → 边界守卫校验 → 候选操作落库 agent_action_logs → 汇总 run 状态。
 * 为什么单独成类：@Async 需经 Spring 代理调用，独立组件避免同类自调用失效。
 */
@Component
public class AgentOrchestrationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationRunner.class);

    private final ObjectProvider<AgentOrchestratorProvider> orchestrationProvider;
    private final AgentRunMapper runMapper;
    private final AgentActionLogMapper logMapper;
    private final WorldFactMapper factMapper;
    private final WorldEntityMapper entityMapper;
    private final WorldEventMapper eventMapper;
    private final AgentActionGuard actionGuard;
    private final AgentPlanAugmenter planAugmenter;
    private final AgentReadOnlyToolService readOnlyToolService;

    @Autowired
    public AgentOrchestrationRunner(ObjectProvider<AgentOrchestratorProvider> orchestrationProvider,
                                    AgentRunMapper runMapper, AgentActionLogMapper logMapper,
                                    WorldFactMapper factMapper, WorldEntityMapper entityMapper,
                                    WorldEventMapper eventMapper, AgentActionGuard actionGuard,
                                    AgentPlanAugmenter planAugmenter,
                                    AgentReadOnlyToolService readOnlyToolService) {
        this.orchestrationProvider = orchestrationProvider;
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.factMapper = factMapper;
        this.entityMapper = entityMapper;
        this.eventMapper = eventMapper;
        this.actionGuard = actionGuard;
        this.planAugmenter = planAugmenter;
        this.readOnlyToolService = readOnlyToolService;
    }

    /**
     * 兼容成员 A 已有的联调测试构造器（7 参数）：默认启用白名单守卫与计划增强，
     * 只读工具执行器置空（测试环境没有 risk/task Mapper），由调用方按需注入真实实现。
     */
    public AgentOrchestrationRunner(ObjectProvider<AgentOrchestratorProvider> orchestrationProvider,
                                    AgentRunMapper runMapper, AgentActionLogMapper logMapper,
                                    WorldFactMapper factMapper, WorldEntityMapper entityMapper,
                                    WorldEventMapper eventMapper, ObjectMapper objectMapper) {
        this(orchestrationProvider, runMapper, logMapper, factMapper, entityMapper, eventMapper,
                new AgentActionGuard(new AgentCapabilityCatalog()), new AgentPlanAugmenter(), null);
    }

    @Async
    public void runAsync(Long runId, Long userId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null) {
            log.warn("编排目标 run 不存在: {}", runId);
            return;
        }
        try {
            AgentOrchestratorProvider provider = orchestrationProvider.getIfAvailable();
            if (provider == null) {
                finish(run, "failed", "AI 编排 Provider 未就绪（等待成员 B 实现 AgentOrchestratorProvider）");
                return;
            }

            PlanContext context = buildRequest(run, userId);
            AgentPlanRequest.AgentPlanResult plan = provider.plan(context.request());
            List<AgentPlanRequest.PlannedAction> actions = planAugmenter.augment(run.getIntent(), plan.actions());
            persistActions(run.getId(), userId, actions, context);
            finish(run, "succeeded", plan.summary());
        } catch (Exception e) {
            log.error("编排执行异常: runId={}", runId, e);
            finish(run, "failed", "编排执行失败: " + e.getMessage());
        }
    }

    /** 用户维度上下文；ownedFactIds / factValues 只来自当前用户的事实，供守卫校验目标归属。 */
    private record PlanContext(AgentPlanRequest request, Set<Long> ownedFactIds, Map<Long, String> factValues) {
    }

    private PlanContext buildRequest(AgentRun run, Long userId) {
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active"));
        Map<Long, WorldEntity> entityById = facts.isEmpty() ? Map.of()
                : entityMapper.selectList(new LambdaQueryWrapper<WorldEntity>()
                        .eq(WorldEntity::getUserId, userId)
                        .in(WorldEntity::getId, facts.stream().map(WorldFact::getSubjectId).toList()))
                        .stream().collect(Collectors.toMap(WorldEntity::getId, Function.identity()));
        List<FactSnapshot> factSnapshots = facts.stream().map(fact -> {
            WorldEntity subject = entityById.get(fact.getSubjectId());
            return new FactSnapshot(fact.getId(), fact.getSubjectId(),
                    subject == null ? null : subject.getEntityType(),
                    subject == null ? null : subject.getName(),
                    fact.getPredicate(), fact.getObjectValue(),
                    fact.getConfidence() == null ? null : fact.getConfidence().doubleValue(),
                    fact.getValidFrom(), fact.getValidUntil());
        }).toList();

        List<EventSnapshot> eventSnapshots = eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .orderByDesc(WorldEvent::getOccurredAt))
                .stream().map(e -> new EventSnapshot(e.getId(), e.getEntityId(), e.getEventType(),
                        e.getTitle(), e.getDescription(), e.getOccurredAt()))
                .toList();

        Map<Long, String> factValues = new HashMap<>();
        facts.forEach(fact -> factValues.put(fact.getId(), fact.getObjectValue()));
        return new PlanContext(
                new AgentPlanRequest(run.getId(), run.getIntent(), factSnapshots, eventSnapshots),
                factValues.keySet(), factValues);
    }

    private void persistActions(Long runId, Long userId, List<AgentPlanRequest.PlannedAction> actions,
                                PlanContext context) {
        if (actions == null) {
            return;
        }
        Instant now = Instant.now();
        for (AgentPlanRequest.PlannedAction action : actions) {
            Map<String, Object> payload = action.payload() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(action.payload());
            // 边界守卫：白名单、级别一致性、越权 userId/factId 一律拒绝落库
            actionGuard.validate(action.actionType(), action.level(), userId, payload, context.ownedFactIds());

            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("action", action.actionType());
            if ("update_fact".equals(action.actionType())) {
                String factId = String.valueOf(payload.get("factId"));
                audit.put("changedField", payload.get("predicate"));
                audit.put("beforeValue", payload.get("currentValue") != null
                        ? String.valueOf(payload.get("currentValue")) : context.factValues().get(Long.valueOf(factId)));
                audit.put("afterValue", payload.get("objectValue"));
            }
            if (readOnlyToolService != null && readOnlyToolService.supports(action.actionType())) {
                Map<String, Object> toolResult = readOnlyToolService.execute(userId, action.actionType(), payload);
                payload.put("toolResult", toolResult);
                audit.put("executionSummary", action.actionType() + " 返回 " + toolResult.get("count") + " 条");
            }
            payload.put(AgentAuditSupport.AUDIT_KEY, audit);

            AgentActionLog log = new AgentActionLog();
            log.setUserId(userId);
            log.setRunId(runId);
            log.setActionType(action.actionType());
            log.setLevel(action.level());
            log.setTargetType(action.targetType());
            log.setTargetId(action.targetId());
            log.setPayload(toJson(payload));
            // L0 只读直接成功；L1/L2 写操作置 pending，等确认/授权后由执行器推进
            log.setResult("L0".equals(action.level()) ? "success" : "pending");
            log.setAuthorized(false);
            log.setCreatedAt(now);
            log.setUpdatedAt(now);
            logMapper.insert(log);
        }
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            // payload 序列化失败不应中断整个 run，降级存空对象
            log.warn("payload 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private void finish(AgentRun run, String status, String summary) {
        Instant now = Instant.now();
        runMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .set(AgentRun::getStatus, status)
                .set(AgentRun::getSummary, summary)
                .set(AgentRun::getFinishedAt, now)
                .set(AgentRun::getUpdatedAt, now));
    }
}
