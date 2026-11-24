package com.nexus.dashboard.service;

import com.nexus.dashboard.dto.DashboardSummaryResponse;
import com.nexus.dashboard.dto.HealthScoreResponse;

/**
 * 驾驶舱聚合统计服务。
 */
public interface DashboardService {

    /** 当前用户的世界模型 / 风险 / 任务 / 通知概览聚合 */
    DashboardSummaryResponse summary(Long userId);

    /** 世界模型健康度评分（数据新鲜度 40 + 风险状况 40 + 任务完成 20） */
    HealthScoreResponse healthScore(Long userId);
}
