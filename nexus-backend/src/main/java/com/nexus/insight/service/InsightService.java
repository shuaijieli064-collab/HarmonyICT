package com.nexus.insight.service;

import com.nexus.insight.dto.InsightOverviewResponse;
import com.nexus.insight.dto.TimelineResponse;

/**
 * 洞察服务（P2 引擎族概览 + P3 未来时间线投影）。只读，不产生数据。
 */
public interface InsightService {

    /** 未 resolved 风险按引擎族分布统计。 */
    InsightOverviewResponse overview(Long userId);

    /** 未来 days 天的时间线投影（1–90 由控制器夹取）。 */
    TimelineResponse timeline(Long userId, int days);
}
