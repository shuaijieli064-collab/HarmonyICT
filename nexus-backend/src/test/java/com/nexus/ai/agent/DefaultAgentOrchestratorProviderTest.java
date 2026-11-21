package com.nexus.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.common.exception.BizException;

class DefaultAgentOrchestratorProviderTest {

    private static final Instant NOW = Instant.now();

    private final AgentToolRegistry registry = new AgentToolRegistry();
    private final DefaultAgentOrchestratorProvider provider = new DefaultAgentOrchestratorProvider(registry,
            new AgentIntentParser());

    @Test
    void summaryIntentProducesOnlyReadActions() {
        AgentPlanRequest request = new AgentPlanRequest(
                1L, "整理本月资产变动", List.of(), List.of());

        AgentPlanRequest.AgentPlanResult result = provider.plan(request);

        assertEquals(2, result.actions().size());
        assertTrue(result.actions().stream().allMatch(action -> "L0".equals(action.level())));
        assertTrue(result.summary().contains("L0 2"));
    }

    @Test
    void createsL1TaskForExpiringWarranty() {
        FactSnapshot warranty = new FactSnapshot(
                100L, 10L, "asset", "笔记本", "warranty_until",
                NOW.plus(5, ChronoUnit.DAYS).toString(), 0.9,
                NOW.minusSeconds(86400), NOW.plus(5, ChronoUnit.DAYS));
        AgentPlanRequest request = new AgentPlanRequest(
                2L, "为快到期的保修创建待办", List.of(warranty), List.of());

        AgentPlanRequest.PlannedAction action = provider.plan(request).actions().get(0);

        assertEquals("create_task", action.actionType());
        assertEquals("L1", action.level());
        assertEquals(100L, action.payload().get("relatedFactId"));
        assertTrue(String.valueOf(action.payload().get("title")).contains("保修"));
        assertNotNull(action.payload().get("dueDate"));
    }

    @Test
    void updateFactRequiresL2Authorization() {
        FactSnapshot fact = new FactSnapshot(
                200L, 20L, "asset", "华为云音乐会员", "subscription_renewal_at",
                "2027-01-18", 0.9, NOW.minusSeconds(86400), null);
        AgentPlanRequest request = new AgentPlanRequest(
                3L, "把华为云音乐会员的续费日期改到 2027-01-18", List.of(fact), List.of());

        AgentPlanRequest.AgentPlanResult result = provider.plan(request);
        AgentPlanRequest.PlannedAction action = result.actions().get(0);

        assertEquals("update_fact", action.actionType());
        assertEquals("L2", action.level());
        assertEquals(200L, action.payload().get("factId"));
        assertEquals("2027-01-18", action.payload().get("proposedValue"));
        assertTrue(result.summary().contains("授权"));
    }

    @Test
    void ambiguousUpdateFallsBackToReadOnly() {
        FactSnapshot first = new FactSnapshot(
                301L, 30L, "asset", "基金A", "price", "1000", 0.9, NOW, null);
        FactSnapshot second = new FactSnapshot(
                302L, 31L, "asset", "基金B", "price", "2000", 0.9, NOW, null);
        AgentPlanRequest request = new AgentPlanRequest(
                4L, "更新一下那个东西", List.of(first, second), List.of());

        AgentPlanRequest.AgentPlanResult result = provider.plan(request);

        assertTrue(result.actions().stream().allMatch(action -> "L0".equals(action.level())));
        assertTrue(result.summary().contains("未定位"));
    }

    @Test
    void emptyIntentProducesNoActions() {
        AgentPlanRequest request = new AgentPlanRequest(5L, "", List.of(), List.of());

        AgentPlanRequest.AgentPlanResult result = provider.plan(request);

        assertTrue(result.actions().isEmpty());
    }

    @Test
    void registryRejectsUnregisteredAction() {
        assertTrue(registry.find("delete_fact").isEmpty());
        assertThrows(BizException.class, () -> registry.require("delete_fact"));
    }
}