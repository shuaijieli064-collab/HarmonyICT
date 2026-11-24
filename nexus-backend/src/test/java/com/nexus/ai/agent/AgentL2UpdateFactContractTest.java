package com.nexus.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.service.impl.AgentActionExecutor;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.task.service.TaskService;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.dto.FactUpdateRequest;
import com.nexus.world.service.WorldEventService;
import com.nexus.world.service.WorldFactService;

/**
 * L2 授权链路联调：B 编排器产出的 update_fact payload，经 A 的 AgentActionExecutor 解析后
 * 真实调用 WorldFactService.update（链式失效：旧事实 superseded + 新事实落库）。
 * 只测契约对齐点（payload 键名 → 执行器解析 → service 调用），不重复测 A 的 service 内部实现。
 */
class AgentL2UpdateFactContractTest {

    private static final long USER_ID = 1L;
    private static final long RUN_ID = 88L;
    private static final long FACT_ID = 300L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentActionLog.class);
    }

    @Test
    void orchestratorUpdateFactPayloadExecutesChainInvalidation() {
        // 1) B 编排器基于真实事实快照产出 L2 update_fact 计划
        FactSnapshot snapshot = new FactSnapshot(FACT_ID, 20L, "asset", "笔记本",
                "warranty_until", "2026-12-01", 0.9,
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.parse("2026-12-01T00:00:00Z"));
        AgentPlanRequest planRequest = new AgentPlanRequest(RUN_ID,
                "把笔记本的保修到期改成2027-06-01", List.of(snapshot), List.of());
        DefaultAgentOrchestratorProvider provider = new DefaultAgentOrchestratorProvider(
                new AgentToolRegistry(), new AgentIntentParser());
        List<AgentPlanRequest.PlannedAction> actions = provider.plan(planRequest).actions();

        AgentPlanRequest.PlannedAction update = actions.stream()
                .filter(a -> "update_fact".equals(a.actionType())).findFirst().orElseThrow();
        assertEquals("L2", update.level());
        assertEquals(FACT_ID, update.payload().get("factId"));
        assertEquals("2027-06-01", update.payload().get("objectValue"));

        // 2) 模拟动作已授权落库（pending + authorized），交给 A 的执行器
        AgentActionLog actionLog = new AgentActionLog();
        actionLog.setId(1L);
        actionLog.setUserId(USER_ID);
        actionLog.setRunId(RUN_ID);
        actionLog.setActionType("update_fact");
        actionLog.setAuthorized(true);
        actionLog.setResult("pending");
        actionLog.setPayload(new ObjectMapper().valueToTree(update.payload()).toString());

        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        WorldEventService eventService = mock(WorldEventService.class);
        when(logMapper.selectList(any())).thenReturn(List.of(actionLog));
        when(factService.update(eq(USER_ID), eq(FACT_ID), any(FactUpdateRequest.class)))
                .thenReturn(new FactResponse(999L, 20L, "warranty_until", "2027-06-01",
                        "date", null, null, null, null, new BigDecimal("0.9"),
                        Instant.now(), Instant.parse("2027-06-01T00:00:00Z"),
                        "active", "confirmed", Instant.now(), Instant.now()));

        AgentActionExecutor executor = new AgentActionExecutor(
                logMapper, taskService, factService, eventService, new ObjectMapper());

        int executed = executor.executeAuthorized(USER_ID, RUN_ID);

        // 3) 断言：执行器按 B 的 payload 键名正确解析，并触发链式失效的 service.update
        assertEquals(1, executed);
        ArgumentCaptor<FactUpdateRequest> captor = ArgumentCaptor.forClass(FactUpdateRequest.class);
        verify(factService).update(eq(USER_ID), eq(FACT_ID), captor.capture());
        assertEquals("2027-06-01", captor.getValue().objectValue());
        assertEquals("success", actionLog.getResult());
        assertEquals("fact", actionLog.getTargetType());
    }

    @Test
    void updateFactPayloadMissingFactIdFailsWithoutExecutingService() {
        AgentActionLog actionLog = new AgentActionLog();
        actionLog.setId(2L);
        actionLog.setUserId(USER_ID);
        actionLog.setRunId(RUN_ID);
        actionLog.setActionType("update_fact");
        actionLog.setAuthorized(true);
        actionLog.setResult("pending");
        // 缺 factId：执行器应抛 PARAM_INVALID，标 failure，不调用 service
        actionLog.setPayload("{\"objectValue\":\"2027-06-01\"}");

        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        when(logMapper.selectList(any())).thenReturn(List.of(actionLog));

        AgentActionExecutor executor = new AgentActionExecutor(
                logMapper, taskService, factService, mock(WorldEventService.class), new ObjectMapper());

        int executed = executor.executeAuthorized(USER_ID, RUN_ID);

        assertEquals(0, executed);
        assertEquals("failure", actionLog.getResult());
        verify(factService, org.mockito.Mockito.never())
                .update(any(), any(), any());
    }
}
