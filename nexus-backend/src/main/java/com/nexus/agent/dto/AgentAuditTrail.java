package com.nexus.agent.dto;

import java.time.Instant;

/**
 * Agent 操作审计摘要。
 * 为什么放在响应 DTO 而不是新增数据库列：agent_action_logs 表结构与契约已定稿，
 * 审计信息随 payload 的 audit 段持久化，查询时解析成结构化字段，避免跨线改 DDL 与契约文档。
 * BIGINT 一律按字符串暴露，避免前端 JS 精度丢失（api-specification.md 第 1 节）。
 *
 * @param action          被授权执行的动作类型
 * @param authorizedBy    授权人 userId（JWT 上下文，不信任前端传参）
 * @param authorizedAt    授权时间
 * @param decision        approved / denied / null（L0 无授权环节）
 * @param changedField    变更字段名（如 warranty_until）
 * @param beforeValue     变更前的世界模型事实值
 * @param afterValue      变更后的事实值
 * @param failureReason   执行失败原因（成功时为 null）
 * @param executionSummary 只读工具执行摘要（供前端时间线展示）
 */
public record AgentAuditTrail(String action, String authorizedBy, Instant authorizedAt, String decision,
                              String changedField, String beforeValue, String afterValue,
                              String failureReason, String executionSummary) {
}
