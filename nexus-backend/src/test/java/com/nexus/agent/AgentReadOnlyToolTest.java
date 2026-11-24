package com.nexus.agent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.agent.service.impl.AgentReadOnlyToolService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只读/建议工具测试：查询强制带 user_id，零写入，不产生新风险结论。
 */
class AgentReadOnlyToolTest {

    private static final long USER_ID = 7L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WorldRisk.class);
        TableInfoHelper.initTableInfo(assistant, WorldRiskFact.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
    }

    private final WorldRiskMapper riskMapper = mock(WorldRiskMapper.class);
    private final WorldRiskFactMapper riskFactMapper = mock(WorldRiskFactMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AgentReadOnlyToolService tools =
            new AgentReadOnlyToolService(riskMapper, riskFactMapper, taskMapper);

    @Test
    void readRiskIsScopedToCurrentUserAndNeverWrites() {
        when(riskMapper.selectList(any())).thenReturn(List.of(risk()));

        Map<String, Object> result = tools.execute(USER_ID, "read_risk", Map.of("limit", 3));

        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WorldRisk>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(riskMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("user_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(USER_ID));
        verify(riskMapper, never()).insert(any(WorldRisk.class));
        verify(riskMapper, never()).update(any(), any());
        verify(riskMapper, never()).delete(any());
    }

    @Test
    void readTaskIsScopedToCurrentUser() {
        Task task = new Task();
        task.setId(12L);
        task.setTitle("续费会员");
        task.setStatus("todo");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        Map<String, Object> result = tools.execute(USER_ID, "read_task", Map.of());

        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Task>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("user_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(USER_ID));
        verify(taskMapper, never()).insert(any(Task.class));
    }

    @Test
    void explainRiskReusesRuleEngineConclusionAndEvidence() {
        when(riskMapper.selectList(any())).thenReturn(List.of(risk()));
        when(riskFactMapper.selectList(any()))
                .thenReturn(List.of(new WorldRiskFact(100L, 900L)));

        Map<String, Object> result = tools.execute(USER_ID, "explain_risk", Map.of());

        assertEquals("CERT_EXPIRY_30D", result.get("ruleCode"));
        assertEquals("证书 30 天内到期", result.get("explanation"));
        assertTrue(String.valueOf(result.get("source")).contains("规则引擎"));
        assertEquals(List.of("900"), result.get("evidenceFactIds"));
        verify(riskFactMapper, never()).insert(any(WorldRiskFact.class));
    }

    @Test
    void suggestActionDoesNotPerformWrites() {
        when(riskMapper.selectList(any())).thenReturn(List.of(risk()));

        Map<String, Object> result = tools.execute(USER_ID, "suggest_action", Map.of("limit", 5));

        assertEquals(false, result.get("writesPerformed"));
        assertTrue(String.valueOf(result.get("suggestions")).contains("创建待办"));
        verify(taskMapper, never()).insert(any(Task.class));
        verify(riskMapper, never()).update(any(), any());
    }

    @Test
    void unregisteredToolReturnsEmpty() {
        assertTrue(tools.execute(USER_ID, "pay_order", Map.of()).isEmpty());
        assertFalse(tools.supports("update_fact"));
    }

    private static WorldRisk risk() {
        WorldRisk risk = new WorldRisk();
        risk.setId(100L);
        risk.setUserId(USER_ID);
        risk.setRiskType("cert_expiry");
        risk.setLevel("high");
        risk.setTitle("证书 30 天内到期");
        risk.setDescription("证书 30 天内到期");
        risk.setRuleCode("CERT_EXPIRY_30D");
        risk.setStatus("open");
        risk.setCreatedAt(Instant.now());
        return risk;
    }
}
