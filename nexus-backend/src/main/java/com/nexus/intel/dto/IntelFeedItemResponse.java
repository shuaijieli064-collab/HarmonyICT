package com.nexus.intel.dto;

import java.time.Instant;

/**
 * 情报流条目：统一聚合风险（risk）、事件（event）、Agent 执行（agent）三类动态。
 * level 仅风险条目有值（low/medium/high），occurredAt 取各来源的业务时间
 * （风险 created_at / 事件 occurred_at / Agent started_at）。
 */
public record IntelFeedItemResponse(String type, Long refId, String title, String description,
                                    String level, String status, Instant occurredAt) {
}
