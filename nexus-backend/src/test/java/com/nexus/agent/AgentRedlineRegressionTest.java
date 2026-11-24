package com.nexus.agent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.agent.service.impl.AgentOrchestrationRunner;
import com.nexus.ai.AgentOrchestratorProvider;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收尾红线回归（编排落库层）：高危动作、SIM_* 推演、跨用户 user_id 一律不得写入 agent_action_logs。
 * 这一层验证的是「即使上游 Provider 产出危险计划，守卫也能在持久化前拦住」。
 */
class AgentRedlineRegressionTest {

    private static final long USER_ID = 3L;
    private static final long RUN_ID = 77L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentRun.class);
        TableInfoHelper.initTableInfo(assistant, AgentActionLog.class);
        TableInfoHelper.initTableInfo(assistant, WorldFact.class);
        TableInfoHelper.initTableInfo(assistant, WorldEntity.class);
    }

    @Test
    void simulationActionIsRejectedBeforePersistence() {
        Fixture f = new Fixture(new AgentPlanRequest.PlannedAction(
                "SIM_KEEP_RENEWAL_30D", "L0", "risk", null,
                Map.of("title", "保留订阅推演"), "推演结果"));

        f.runner().runAsync(RUN_ID, USER_ID);

        verify(f.logMapper, never()).insert(any(AgentActionLog.class));
        verify(f.runMapper).update(isNull(), any());
    }

    @Test
    void paymentActionIsRejectedBeforePersistence() {
        Fixture f = new Fixture(new AgentPlanRequest.PlannedAction(
                "pay_order", "L2", "order", null,
                Map.of("orderId", 1L), "支付"));

        f.runner().runAsync(RUN_ID, USER_ID);

        verify(f.logMapper, never()).insert(any(AgentActionLog.class));
        verify(f.runMapper).update(isNull(), any());
    }

    @Test
    void foreignUserIdInPayloadIsRejectedBeforePersistence() {
        Fixture f = new Fixture(new AgentPlanRequest.PlannedAction(
                "read_task", "L0", "task", null,
                Map.of("userId", 999L, "limit", 5), "读取他人数据"));

        f.runner().runAsync(RUN_ID, USER_ID);

        verify(f.logMapper, never()).insert(any(AgentActionLog.class));
    }

    @Test
    void safeReadOnlyActionStillPersistsWithAudit() {
        Fixture f = new Fixture(new AgentPlanRequest.PlannedAction(
                "read_risk", "L0", "risk", null,
                Map.of("limit", 5), "读取风险"));

        f.runner().runAsync(RUN_ID, USER_ID);

        ArgumentCaptor<AgentActionLog> captor = ArgumentCaptor.forClass(AgentActionLog.class);
        verify(f.logMapper).insert(captor.capture());
        AgentActionLog log = captor.getValue();
        assertTrue(log.getPayload().contains("audit"));
        assertTrue(log.getPayload().contains("read_risk"));
        assertTrue("success".equals(log.getResult()));
    }

    private static final class Fixture {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        final WorldFactMapper factMapper = mock(WorldFactMapper.class);
        final WorldEntityMapper entityMapper = mock(WorldEntityMapper.class);
        final WorldEventMapper eventMapper = mock(WorldEventMapper.class);

        Fixture(AgentPlanRequest.PlannedAction planned) {
            AgentRun run = new AgentRun();
            run.setId(RUN_ID);
            run.setUserId(USER_ID);
            run.setStatus("running");
            run.setIntent("测试动作");
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(factMapper.selectList(any())).thenReturn(List.of());
            when(entityMapper.selectList(any())).thenReturn(List.of());
            when(eventMapper.selectList(any())).thenReturn(List.of());

            AgentOrchestratorProvider provider = mock(AgentOrchestratorProvider.class);
            when(provider.plan(any())).thenReturn(new AgentPlanRequest.AgentPlanResult(
                    "测试计划", List.of(planned)));
            @SuppressWarnings("unchecked")
            ObjectProvider<AgentOrchestratorProvider> providerHolder = mock(ObjectProvider.class);
            when(providerHolder.getIfAvailable()).thenReturn(provider);
            this.providerHolder = providerHolder;
        }

        private final ObjectProvider<AgentOrchestratorProvider> providerHolder;

        AgentOrchestrationRunner runner() {
            return new AgentOrchestrationRunner(providerHolder, runMapper, logMapper,
                    factMapper, entityMapper, eventMapper, new ObjectMapper());
        }
    }
}
