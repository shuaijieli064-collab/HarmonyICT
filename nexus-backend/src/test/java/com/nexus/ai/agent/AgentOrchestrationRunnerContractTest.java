package com.nexus.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.agent.service.impl.AgentOrchestrationRunner;
import com.nexus.ai.AgentOrchestratorProvider;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;

/**
 * 联调自测：A 的 AgentOrchestrationRunner 经 ObjectProvider 注入 B 的编排 Provider，
 * 在真实 run 上下文（run + facts + entities）下产出计划并落库 action logs。
 * 对应 A 消息第③项：不需要 A 改代码，B 自测 plan() 被真实调用后的产出。
 */
class AgentOrchestrationRunnerContractTest {

    private static final long RUN_ID = 77L;
    private static final long USER_ID = 1L;

    /** 无 Spring 上下文时手动初始化 MyBatis-Plus 实体元数据，否则 LambdaQueryWrapper 找不到列缓存。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentRun.class);
        TableInfoHelper.initTableInfo(assistant, WorldFact.class);
        TableInfoHelper.initTableInfo(assistant, WorldEntity.class);
        TableInfoHelper.initTableInfo(assistant, AgentActionLog.class);
    }

    @Test
    void summaryIntentThroughRunnerPersistsL0ReadOnlyPlan() {
        Fixture f = new Fixture("整理本月资产变动", List.of(), List.of());

        f.runner().runAsync(RUN_ID, USER_ID);

        ArgumentCaptor<AgentActionLog> logs = ArgumentCaptor.forClass(AgentActionLog.class);
        verify(f.logMapper, times(2)).insert(logs.capture());
        assertTrue(logs.getAllValues().stream().allMatch(log -> "L0".equals(log.getLevel())));
        assertTrue(logs.getAllValues().stream().allMatch(log -> "success".equals(log.getResult())));
        assertTrue(logs.getAllValues().stream().allMatch(log -> Boolean.FALSE.equals(log.getAuthorized())));
        assertEquals(List.of("read_fact", "read_event"),
                logs.getAllValues().stream().map(AgentActionLog::getActionType).toList());
        verify(f.runMapper).update(isNull(), any());
    }

    @Test
    void expiringWarrantyThroughRunnerPersistsL1TaskPending() {
        Instant now = Instant.now();
        WorldFact warranty = fact(100L, 10L, "warranty_until",
                now.plus(5, ChronoUnit.DAYS).toString(), now.plus(5, ChronoUnit.DAYS));
        WorldEntity laptop = entity(10L, "asset", "笔记本");
        Fixture f = new Fixture("为快到期的保修创建待办", List.of(warranty), List.of(laptop));

        f.runner().runAsync(RUN_ID, USER_ID);

        ArgumentCaptor<AgentActionLog> logs = ArgumentCaptor.forClass(AgentActionLog.class);
        verify(f.logMapper).insert(logs.capture());
        AgentActionLog log = logs.getValue();
        assertEquals("create_task", log.getActionType());
        assertEquals("L1", log.getLevel());
        assertEquals("pending", log.getResult());
        assertTrue(log.getPayload().contains("保修"));
        assertTrue(log.getPayload().contains("relatedFactId"));
    }

    private static WorldFact fact(long id, long subjectId, String predicate, String value, Instant validUntil) {
        WorldFact fact = new WorldFact();
        fact.setId(id);
        fact.setUserId(USER_ID);
        fact.setSubjectId(subjectId);
        fact.setPredicate(predicate);
        fact.setObjectValue(value);
        fact.setConfidence(new BigDecimal("0.9"));
        fact.setStatus("active");
        fact.setFactStatus("confirmed");
        fact.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        fact.setValidUntil(validUntil);
        return fact;
    }

    private static WorldEntity entity(long id, String type, String name) {
        WorldEntity entity = new WorldEntity();
        entity.setId(id);
        entity.setUserId(USER_ID);
        entity.setEntityType(type);
        entity.setName(name);
        return entity;
    }

    /** 用 mock mapper 承接 A 的 Runner 依赖，Provider 用 B 的真实实现。 */
    private static final class Fixture {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentActionLogMapper logMapper = mock(AgentActionLogMapper.class);
        final WorldFactMapper factMapper = mock(WorldFactMapper.class);
        final WorldEntityMapper entityMapper = mock(WorldEntityMapper.class);
        final WorldEventMapper eventMapper = mock(WorldEventMapper.class);

        Fixture(String intent, List<WorldFact> facts, List<WorldEntity> entities) {
            AgentRun run = new AgentRun();
            run.setId(RUN_ID);
            run.setUserId(USER_ID);
            run.setIntent(intent);
            run.setStatus("running");
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(factMapper.selectList(any())).thenReturn(facts);
            when(entityMapper.selectList(any())).thenReturn(entities);
            when(eventMapper.selectList(any())).thenReturn(List.of());
        }

        AgentOrchestrationRunner runner() {
            @SuppressWarnings("unchecked")
            ObjectProvider<AgentOrchestratorProvider> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(
                    new DefaultAgentOrchestratorProvider(new AgentToolRegistry(), new AgentIntentParser()));
            return new AgentOrchestrationRunner(provider, runMapper, logMapper,
                    factMapper, entityMapper, eventMapper, new ObjectMapper());
        }
    }
}
