package com.nexus.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

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
import com.nexus.task.service.TaskService;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.EventResponse;
import com.nexus.world.service.WorldEventService;
import com.nexus.world.service.WorldFactService;

/**
 * 新增 L2 工具 create_event 授权链路联调：编排器把「日程」类意图产出 create_event 计划，
 * 经 AgentActionExecutor 授权后真实调用 WorldEventService.create 落事件时间线。
 */
class AgentL2CreateEventContractTest {

    private static final long USER_ID = 1L;
    private static final long RUN_ID = 90L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentActionLog.class);
    }

    @Test
    void orchestratorCreateEventPayloadExecutesEventCreation() {
        // 1) 编排器：意图含「日程」与日期 → CREATE_EVENT 计划
        AgentPlanRequest planRequest = new AgentPlanRequest(RUN_ID,
                "2026年11月25日安排小组评审日程", List.of(), List.of());
        DefaultAgentOrchestratorProvider provider = new DefaultAgentOrchestratorProvider(
                new AgentToolRegistry(), new AgentIntentParser());
        List<AgentPlanRequest.PlannedAction> actions = provider.plan(planRequest).actions();

        AgentPlanRequest.PlannedAction plan = actions.stream()
                .filter(a -> "create_event".equals(a.actionType())).findFirst().orElseThrow();
        assertEquals("L2", plan.level());
        assertEquals("other", plan.payload().get("eventType"));
        assertEquals("2026-11-25T00:00:00Z", plan.payload().get("occurredAt"));

        // 2) 模拟已授权动作，交给执行器
        AgentActionLog actionLog = new AgentActionLog();
        actionLog.setId(1L);
        actionLog.setUserId(USER_ID);
        actionLog.setRunId(RUN_ID);
        actionLog.setActionType("create_event");
        actionLog.setAuthorized(true);
        actionLog.setResult("pending");
        actionLog.setPayload(new ObjectMapper().valueToTree(plan.payload()).toString());

        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        WorldEventService eventService = mock(WorldEventService.class);
        when(logMapper.selectList(any())).thenReturn(List.of(actionLog));
        when(eventService.create(eq(USER_ID), any(EventCreateRequest.class)))
                .thenReturn(new EventResponse(501L, null, "other", "日程", null,
                        Instant.parse("2026-11-25T00:00:00Z"), null, Instant.now(), Instant.now()));

        AgentActionExecutor executor = new AgentActionExecutor(
                logMapper, taskService, factService, eventService, new ObjectMapper());

        int executed = executor.executeAuthorized(USER_ID, RUN_ID);

        // 3) 断言：执行器按 payload 键名解析并真实调用事件创建
        assertEquals(1, executed);
        ArgumentCaptor<EventCreateRequest> captor = ArgumentCaptor.forClass(EventCreateRequest.class);
        verify(eventService).create(eq(USER_ID), captor.capture());
        assertEquals("other", captor.getValue().eventType());
        assertEquals(Instant.parse("2026-11-25T00:00:00Z"), captor.getValue().occurredAt());
        assertEquals("success", actionLog.getResult());
        assertEquals("event", actionLog.getTargetType());
        assertEquals(501L, actionLog.getTargetId());
    }

    @Test
    void createEventPayloadMissingTitleFailsWithoutExecutingService() {
        AgentActionLog actionLog = new AgentActionLog();
        actionLog.setId(2L);
        actionLog.setUserId(USER_ID);
        actionLog.setRunId(RUN_ID);
        actionLog.setActionType("create_event");
        actionLog.setAuthorized(true);
        actionLog.setResult("pending");
        actionLog.setPayload("{\"eventType\":\"other\"}");

        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        WorldEventService eventService = mock(WorldEventService.class);
        when(logMapper.selectList(any())).thenReturn(List.of(actionLog));

        AgentActionExecutor executor = new AgentActionExecutor(
                logMapper, taskService, factService, eventService, new ObjectMapper());

        assertEquals(0, executor.executeAuthorized(USER_ID, RUN_ID));
        assertEquals("failure", actionLog.getResult());
        verify(eventService, org.mockito.Mockito.never()).create(any(), any());
    }
}
