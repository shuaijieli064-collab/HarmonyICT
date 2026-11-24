package com.nexus.agent.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 动作落库前的边界守卫。职责：把「L0–L2 + 数据隔离」从约定变成硬校验。
 * 为什么独立成类：编排、授权、执行三条路径都要复用同一套判定，避免各处散落 if。
 */
@Component
public class AgentActionGuard {

    private static final Set<String> FORBIDDEN_PAYLOAD_KEYS = Set.of(
            "webhook", "callbackUrl", "externalUrl", "accountNo", "cardNo", "amountToPay");

    private final AgentCapabilityCatalog catalog;

    public AgentActionGuard(AgentCapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 校验一个候选动作是否允许为本用户落库。
     *
     * @param ownedFactIds 当前 run 上下文中属于该用户的事实 id 集合（由 user_id 过滤后的查询结果）
     */
    public void validate(String actionType, String level, Long userId,
                         Map<String, Object> payload, Set<Long> ownedFactIds) {
        AgentCapabilityCatalog.Tool tool = catalog.require(actionType);

        if (AgentCapabilityCatalog.isForbidden(actionType)) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "禁止支付/删除/外部高风险动作: " + actionType);
        }
        // 编排器不得自行改变能力目录定义的级别（例如把 L2 伪装成 L0 绕过授权）
        if (!tool.level().equals(level)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "动作级别与能力目录不一致: " + actionType + " 应为 " + tool.level());
        }
        if (payload == null || payload.isEmpty()) {
            return;
        }
        // 红线：user_id 只从 JWT 取，payload 中若带且不匹配一律拒绝（无越权操作）
        Object suppliedUserId = payload.get("userId");
        if (suppliedUserId != null && !String.valueOf(userId).equals(String.valueOf(suppliedUserId))) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "Agent 动作携带了非当前用户的 userId");
        }
        for (String key : FORBIDDEN_PAYLOAD_KEYS) {
            if (payload.containsKey(key)) {
                throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "Agent 动作包含高风险载荷字段: " + key);
            }
        }
        // 事实类目标必须属于当前用户，防止通过 factId/relatedFactId 跨用户读写
        Long factId = toLong(payload.get("factId"));
        Long relatedFactId = toLong(payload.get("relatedFactId"));
        if (factId != null && !ownedFactIds.contains(factId)) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "目标事实不属于当前用户: " + factId);
        }
        if (relatedFactId != null && !ownedFactIds.contains(relatedFactId)) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "关联事实不属于当前用户: " + relatedFactId);
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "事实 id 不是合法数字: " + value);
        }
    }
}
