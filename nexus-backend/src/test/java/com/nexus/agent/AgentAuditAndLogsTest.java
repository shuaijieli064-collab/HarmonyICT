package com.nexus.agent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.dto.AgentAuditTrail;
import com.nexus.agent.dto.AgentRunResponse;
import com.nexus.agent.dto.RunAuthorizeRequest;
import com.nexus.agent.dto.RunDetailResponse;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.agent.service.AgentRunService;
import com.nexus.agent.service.impl.AgentActionExecutor;
import com.nexus.agent.service.impl.AgentOrchestrationRunner;
import com.nexus.agent.service.impl.AgentRunServiceImpl;
import com.nexus.agent.util.AgentAuditSupport;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * V3 审计与日志接口测试：授权人/时间/前后差异可追溯，越权 run/log 一律拒绝。
 */
class AgentAuditAndLogsTest {

    private static final long USER_ID = 42L;
    private static final long RUN_ID = 88L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentRun.class);
        TableInfoHelper.initTableInfo(assistant, AgentActionLog.class);
    }

    @Test
    void auditRoundTripKeepsAuthorizerTimeAndBeforeAfterValues() {
        String json = AgentAuditSupport.write("{\"factId\":1,\"objectValue\":\"2027-01-01\"}", Map.of(
                "action", "update_fact",
                "authorizedBy", "42",
                "authorizedAt", "2026-11-24T01:02:03Z",
                "decision", "approved",
                "changedField", "warranty_until",
                "beforeValue", "2026-12-01",
                "afterValue", "2027-01-01"));

        AgentAuditTrail audit = AgentAuditSupport.read(json);

        assertEquals("update_fact", audit.action());
        assertEquals("42", audit.authorizedBy());
        assertEquals(Instant.parse("2026-11-24T01:02:03Z"), audit.authorizedAt());
        assertEquals("approved", audit.decision());
        assertEquals("2026-12-01", audit.beforeValue());
        assertEquals("2027-01-01", audit.afterValue());
        // 原始 payload 字段不能被审计写入破坏，执行器仍可解析 factId/objectValue
        assertEquals(1L, ((Number) AgentAuditSupport.parseMap(json).get("factId")).longValue());
    }

    @Test
    void authorizeWritesAuditAndExecutesOnlyOwnedPendingL2Log() {
        AgentRun run = ownedRun();
        AgentActionLog target = pendingL2Log(11L);
        Fixture f = new Fixture(run, List.of(target));

        f.service().authorize(USER_ID, RUN_ID, new RunAuthorizeRequest(List.of(11L), true));

        ArgumentCaptor<AgentActionLog> captor = ArgumentCaptor.forClass(AgentActionLog.class);
        verify(f.logMapper).updateById(captor.capture());
        AgentActionLog saved = captor.getValue();
        assertTrue(saved.getAuthorized());
        AgentAuditTrail audit = AgentAuditSupport.read(saved.getPayload());
        assertEquals("42", audit.authorizedBy());
        assertEquals("approved", audit.decision());
        assertEquals("update_fact", audit.action());
        assertNotNull(audit.authorizedAt());
        verify(f.executor).executeAuthorized(USER_ID, RUN_ID);
    }

    @Test
    void authorizeRejectsForeignLogIdsWithoutAnyWrite() {
        Fixture f = new Fixture(ownedRun(), List.of());

        BizException ex = assertThrows(BizException.class,
                () -> f.service().authorize(USER_ID, RUN_ID, new RunAuthorizeRequest(List.of(999L), true)));

        assertEquals(ResultCode.AGENT_PERMISSION_DENIED.getCode(), ex.getResultCode().getCode());
        verify(f.logMapper, never()).updateById(any(AgentActionLog.class));
        verify(f.executor, never()).executeAuthorized(any(), any());
    }

    @Test
    void logsEndpointRejectsForeignRunAndScopesQueriesByUserId() {
        Fixture f = new Fixture(ownedRun(), List.of(pendingL2Log(11L)));
        when(f.runMapper.selectOne(any())).thenReturn(null);
        assertThrows(BizException.class,
                () -> f.service().listLogs(999L, RUN_ID, null, null, 1, 20));

        when(f.runMapper.selectOne(any())).thenReturn(ownedRun());
        Page<AgentActionLog> page = new Page<>(1, 20);
        page.setRecords(List.of(pendingL2Log(11L)));
        page.setTotal(1);
        when(f.logMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<?> result = f.service().listLogs(USER_ID, RUN_ID, "L2", "pending", 1, 20);

        assertEquals(1, result.records().size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AgentActionLog>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(f.logMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("user_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(USER_ID));
    }

    private static AgentRun ownedRun() {
        AgentRun run = new AgentRun();
        run.setId(RUN_ID);
        run.setUserId(USER_ID);
        run.setStatus("succeeded");
        run.setIntent("把保修到期改成2027-01-01");
        return run;
    }

    private static AgentActionLog pendingL2Log(Long id) {
        AgentActionLog log = new AgentActionLog();
        log.setId(id);
        log.setUserId(USER_ID);
        log.setRunId(RUN_ID);
        log.setActionType("update_fact");
        log.setLevel("L2");
        log.setResult("pending");
        log.setAuthorized(false);
        log.setPayload("{\"factId\":1,\"predicate\":\"warranty_until\","
                + "\"currentValue\":\"2026-12-01\",\"objectValue\":\"2027-01-01\"}");
        return log;
    }

    private static final class Fixture {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        final AgentActionExecutor executor = mock(AgentActionExecutor.class);
        final AgentRunService selfProxy = mock(AgentRunService.class);

        Fixture(AgentRun run, List<AgentActionLog> logs) {
            when(runMapper.selectOne(any())).thenReturn(run);
            when(logMapper.selectList(any())).thenReturn(logs);
            when(selfProxy.get(eq(USER_ID), eq(RUN_ID)))
                    .thenReturn(new RunDetailResponse(AgentRunResponse.from(run), List.of()));
        }

        AgentRunService service() {
            return new AgentRunServiceImpl(runMapper, logMapper, mock(AgentOrchestrationRunner.class),
                    executor, selfProxy);
        }
    }
}
