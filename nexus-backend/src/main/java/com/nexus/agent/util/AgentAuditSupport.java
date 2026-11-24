package com.nexus.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.dto.AgentAuditTrail;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计字段读写工具。所有审计信息存在 agent_action_logs.payload 的顶层 audit 对象中，
 * 保证表结构零变更；解析失败时返回空审计而不是抛异常，避免历史数据导致详情接口 500。
 */
public final class AgentAuditSupport {

    public static final String AUDIT_KEY = "audit";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentAuditSupport() {
    }

    /** 把若干审计字段写入 audit 段，返回新的 payload JSON 字符串。 */
    public static String write(String payloadJson, Map<String, Object> auditFields) {
        Map<String, Object> payload = parseMap(payloadJson);
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = payload.get(AUDIT_KEY) instanceof Map<?, ?> existing
                ? (Map<String, Object>) existing : new LinkedHashMap<String, Object>();
        audit.putAll(auditFields);
        payload.put(AUDIT_KEY, audit);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            // 审计写入失败不能吞掉原 payload；退化返回原值由调用方决定是否继续
            return payloadJson == null ? "{}" : payloadJson;
        }
    }

    /** 便捷重载：写入单个审计字段。 */
    public static String write(String payloadJson, String key, Object value) {
        return write(payloadJson, Map.of(key, value));
    }

    /** 将 payload（含 audit 段）解析为稳定的审计 DTO。 */
    public static AgentAuditTrail read(String payloadJson) {
        Map<String, Object> payload = parseMap(payloadJson);
        Object rawAudit = payload.get(AUDIT_KEY);
        Map<String, Object> audit = rawAudit instanceof Map<?, ?> map
                ? castMap(map) : Map.of();
        return new AgentAuditTrail(
                text(audit.get("action")),
                text(audit.get("authorizedBy")),
                instant(audit.get("authorizedAt")),
                text(audit.get("decision")),
                text(audit.get("changedField")),
                text(audit.get("beforeValue")),
                text(audit.get("afterValue")),
                text(audit.get("failureReason")),
                text(audit.get("executionSummary")));
    }

    /** 读取 payload 顶层字段，兼容旧数据。 */
    public static Map<String, Object> parseMap(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(payloadJson, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
