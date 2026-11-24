package com.nexus.intel.service;

import com.nexus.common.result.PageResult;
import com.nexus.intel.dto.IntelFeedItemResponse;
import com.nexus.intel.dto.IntelSummaryResponse;

/**
 * 情报中心服务：风险 / 事件 / Agent 执行的只读聚合视图。
 */
public interface IntelService {

    /** 汇总统计：风险分布、近 7 天事件、未完成任务、近 7 天 Agent 执行。 */
    IntelSummaryResponse summary(Long userId);

    /**
     * 情报流：三类动态按时间倒序合并分页。
     * @param type all/risk/event/agent，其他取值按 all 处理
     * @param days 最近天数窗口（1-90，越界收敛到默认 30）
     */
    PageResult<IntelFeedItemResponse> feed(Long userId, String type, int days, int page, int pageSize);
}
