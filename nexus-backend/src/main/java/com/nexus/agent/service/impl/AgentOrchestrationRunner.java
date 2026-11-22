package com.nexus.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.AgentOrchestratorProvider;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 异步编排执行器。
 * 职责（A 侧）：组装上下文快照 → 调用 AgentOrchestratorProvider.plan（B 实现）→
 * 候选操作落库 agent_action_logs（L0 直接 success，L1/L2 置 pending 待确认/授权）→ 汇总 run 状态。
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
    private final ObjectMapper objectMapper;

    public AgentOrchestrationRunner(ObjectProvider<AgentOrchestratorProvider> orchestrationProvider,
                                    AgentRunMapper runMapper, AgentActionLogMapper logMapper,
                                    WorldFactMapper factMapper, WorldEntityMapper entityMapper,
                                    WorldEventMapper eventMapper, ObjectMapper objectMapper) {
        this.orchestrationProvider = orchestrationProvider;
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.factMapper = factMapper;
        this.entityMapper = entityMapper;
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
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

            AgentPlanRequest.AgentPlanResult plan = provider.plan(buildRequest(run, userId));
            persistActions(run.getId(), userId, plan.actions());
            finish(run, "succeeded", plan.summary());
        } catch (Exception e) {
            log.error("编排执行异常: runId={}", runId, e);
            finish(run, "failed", "编排执行失败: " + e.getMessage());
        }
    }

    private AgentPlanRequest buildRequest(AgentRun run, Long userId) {
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

        return new AgentPlanRequest(run.getId(), run.getIntent(), factSnapshots, eventSnapshots);
    }

    private void persistActions(Long runId, Long userId, List<AgentPlanRequest.PlannedAction> actions) {
        if (actions == null) {
            return;
        }
        Instant now = Instant.now();
        for (AgentPlanRequest.PlannedAction action : actions) {
            AgentActionLog log = new AgentActionLog();
            log.setUserId(userId);
            log.setRunId(runId);
            log.setActionType(action.actionType());
            log.setLevel(action.level());
            log.setTargetType(action.targetType());
            log.setTargetId(action.targetId());
            log.setPayload(toJson(action.payload()));
            // L0 只读直接成功；L1/L2 写操作置 pending，等确认/授权后由执行器推进（阶段四联调）
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
            return objectMapper.writeValueAsString(payload);
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