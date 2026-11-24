package com.nexus.agent;

import com.nexus.agent.service.impl.AgentActionGuard;
import com.nexus.agent.service.impl.AgentCapabilityCatalog;
import com.nexus.agent.service.impl.AgentPlanAugmenter;
import com.nexus.ai.dto.AgentPlanRequest;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 边界测试：L0–L2 之外无动作、无支付/删除动作、无越权 user_id/factId。
 */
class AgentBoundaryGuardTest {

    private static final long USER_ID = 42L;

    private final AgentCapabilityCatalog catalog = new AgentCapabilityCatalog();
    private final AgentActionGuard guard = new AgentActionGuard(catalog);

    @Test
    void paymentAndDeletionActionsAreNeverRegistered() {
        for (String action : List.of("pay_order", "transfer_fund", "withdraw", "delete_fact", "external_call")) {
            assertTrue(AgentCapabilityCatalog.isForbidden(action), action + " 应被拒绝");
            assertTrue(catalog.find(action).isEmpty(), action + " 不应出现在能力目录");
            assertThrows(BizException.class, () -> catalog.require(action));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AgentCapabilityCatalog.Tool("read_all", "L3", "fact", "越权级别"));
    }

    @Test
    void catalogContainsReadOnlyAndAdviceToolsWithinL0ToL2() {
        assertTrue(catalog.all().stream().allMatch(t -> List.of("L0", "L1", "L2").contains(t.level())));
        assertEquals("L0", catalog.require("read_risk").level());
        assertEquals("L0", catalog.require("explain_risk").level());
        assertEquals("L0", catalog.require("suggest_action").level());
        assertEquals("L2", catalog.require("update_fact").level());
    }

    @Test
    void levelDowngradeAndUnknownActionAreRejected() {
        assertThrows(BizException.class,
                () -> guard.validate("update_fact", "L0", USER_ID, Map.of("factId", 1L), Set.of(1L)));
        BizException unknown = assertThrows(BizException.class,
                () -> guard.validate("send_notification", "L1", USER_ID, Map.of(), Set.of()));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), unknown.getResultCode().getCode());
    }

    @Test
    void foreignUserIdAndFactIdAreRejected() {
        BizException crossUser = assertThrows(BizException.class,
                () -> guard.validate("read_risk", "L0", USER_ID, Map.of("userId", 999L), Set.of()));
        assertEquals(ResultCode.AGENT_PERMISSION_DENIED.getCode(), crossUser.getResultCode().getCode());

        assertThrows(BizException.class,
                () -> guard.validate("update_fact", "L2", USER_ID,
                        Map.of("factId", 999L, "objectValue", "x"), Set.of(1L)));
        assertThrows(BizException.class,
                () -> guard.validate("create_task", "L1", USER_ID,
                        Map.of("title", "t", "relatedFactId", "999"), Set.of(1L)));
    }

    @Test
    void highRiskPayloadFieldsAreRejected() {
        assertThrows(BizException.class,
                () -> guard.validate("create_task", "L1", USER_ID,
                        Map.of("title", "t", "amountToPay", 100), Set.of()));
        assertThrows(BizException.class,
                () -> guard.validate("create_task", "L1", USER_ID,
                        Map.of("title", "t", "externalUrl", "https://example.com"), Set.of()));
    }

    @Test
    void ownedReadOnlyToolPassesGuard() {
        guard.validate("read_risk", "L0", USER_ID, Map.of("limit", 5), Set.of());
        guard.validate("update_fact", "L2", USER_ID,
                Map.of("factId", 1L, "objectValue", "2027-01-01"), Set.of(1L));
    }

    @Test
    void simulationRisksNeverBecomeAgentActions() {
        for (String sim : List.of("SIM_KEEP_RENEWAL_30D", "SIM_KEEP_BUDGET_OVER", "SIM_CANCEL_RENEWAL")) {
            assertTrue(catalog.find(sim).isEmpty(), sim + " 不应进入 Agent 能力目录");
            assertThrows(BizException.class,
                    () -> guard.validate(sim, "L0", USER_ID, Map.of("title", "推演"), Set.of()));
        }
        assertTrue(catalog.all().stream().noneMatch(t -> t.actionType().startsWith("SIM_")));
        assertTrue(new AgentPlanAugmenter().augment("解释 SIM_KEEP_RENEWAL_30D 推演", List.of()).stream()
                .noneMatch(a -> a.actionType().startsWith("SIM_")));
    }

    @Test
    void augmenterAddsOnlyReadOnlyToolsForQueryIntent() {
        AgentPlanAugmenter augmenter = new AgentPlanAugmenter();
        List<AgentPlanRequest.PlannedAction> base = List.of(
                new AgentPlanRequest.PlannedAction("read_fact", "L0", "fact", null, Map.of(), "理由"));

        List<AgentPlanRequest.PlannedAction> riskPlan =
                augmenter.augment("查看有哪些风险并解释", base);
        assertTrue(riskPlan.stream().anyMatch(a -> "read_risk".equals(a.actionType())));
        assertTrue(riskPlan.stream().anyMatch(a -> "explain_risk".equals(a.actionType())));
        assertTrue(riskPlan.stream().allMatch(a -> "L0".equals(a.level())));

        List<AgentPlanRequest.PlannedAction> summary =
                augmenter.augment("整理本月资产变动", base);
        assertEquals(1, summary.size(), "汇总意图不应被追加工具，保持既有编排契约");

        List<AgentPlanRequest.PlannedAction> task =
                augmenter.augment("为快到期的保修创建待办", List.of(
                        new AgentPlanRequest.PlannedAction("create_task", "L1", "task", null, Map.of(), "理由")));
        assertEquals(1, task.size(), "创建待办意图不应被追加只读工具");
        assertFalse(task.get(0).actionType().equals("read_task"));
    }
}
