package com.nexus.dashboard.service;

import com.nexus.dashboard.dto.DashboardSummaryResponse;

/**
 * 驾驶舱聚合统计服务。
 */
public interface DashboardService {

    /** 当前用户的世界模型 / 风险 / 任务 / 通知概览聚合 */
    DashboardSummaryResponse summary(Long userId);
}
