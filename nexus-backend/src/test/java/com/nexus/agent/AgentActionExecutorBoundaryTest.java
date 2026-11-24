package com.nexus.agent;

import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.service.impl.AgentActionExecutor;
import com.nexus.agent.util.AgentAuditSupport;
import com.nexus.task.service.TaskService;
import com.nexus.world.service.WorldEventService;
import com.nexus.world.service.WorldFactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行路径边界：未授权不执行、高危动作在执行前被拦截且失败原因可追溯。
 */
class AgentActionExecutorBoundaryTest {

    private static final long USER_ID = 5L;
    private static final long RUN_ID = 9L;

    @Test
    void unauthorizedActionIsNeverExecuted() {
        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        // 模拟查询条件生效：执行器只会拿到 authorized=true 的日志
        when(logMapper.selectList(any())).thenReturn(List.of());

        new AgentActionExecutor(logMapper, taskService, factService,
                org.mockito.Mockito.mock(WorldEventService.class), new ObjectMapper())
                .executeAuthorized(USER_ID, RUN_ID);

        verify(taskService, never()).create(any(), any());
        verify(factService, never()).update(any(), any(), any());
    }

    @Test
    void forbiddenActionFailsBeforeAnyBusinessServiceCall() {
        AgentActionLog log = new AgentActionLog();
        log.setId(1L);
        log.setUserId(USER_ID);
        log.setRunId(RUN_ID);
        log.setActionType("pay_order");
        log.setLevel("L2");
        log.setAuthorized(true);
        log.setResult("pending");
        log.setPayload("{}");

        AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        TaskService taskService = mock(TaskService.class);
        WorldFactService factService = mock(WorldFactService.class);
        when(logMapper.selectList(any())).thenReturn(List.of(log));

        int executed = new AgentActionExecutor(logMapper, taskService, factService,
                org.mockito.Mockito.mock(WorldEventService.class), new ObjectMapper())
                .executeAuthorized(USER_ID, RUN_ID);

        assertEquals(0, executed);
        assertEquals("failure", log.getResult());
        Map<String, Object> payload = AgentAuditSupport.parseMap(log.getPayload());
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) payload.get("audit");
        assertTrue(String.valueOf(audit.get("failureReason")).contains("高风险动作"));
        verify(taskService, never()).create(any(), any());
        verify(factService, never()).update(any(), any(), any());
    }
}
