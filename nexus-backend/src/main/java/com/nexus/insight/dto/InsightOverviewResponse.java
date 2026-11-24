package com.nexus.insight.dto;

import java.util.List;

/**
 * 引擎族概览响应（P2）：GET /api/v1/insight/overview。
 * 仅统计未 resolved 的风险；families 按引擎族枚举顺序返回，零风险族不出现。
 */
public record InsightOverviewResponse(long totalOpen, List<EngineFamilySummary> families) {
}
