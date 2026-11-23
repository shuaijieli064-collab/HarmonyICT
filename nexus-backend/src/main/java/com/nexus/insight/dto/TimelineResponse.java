package com.nexus.insight.dto;

import java.util.List;

/**
 * 未来 N 天时间线响应（P3）：GET /api/v1/insight/timeline?days=30。
 */
public record TimelineResponse(int days, long count, List<TimelineItem> items) {
}
