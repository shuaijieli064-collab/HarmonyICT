package com.nexus.asset.service;

import com.nexus.asset.dto.AssetResponse;
import com.nexus.asset.dto.AssetSummaryResponse;
import com.nexus.common.result.PageResult;

/**
 * 资产中心服务：世界实体 + 事实的只读聚合视图，不产生新数据。
 */
public interface AssetService {

    /** 资产列表（实体分页 + 关联事实聚合），entityType / keyword 可选过滤。 */
    PageResult<AssetResponse> list(Long userId, String entityType, String keyword, int page, int pageSize);

    /** 资产汇总：按类型统计、价格类事实求和、未关闭风险数。 */
    AssetSummaryResponse summary(Long userId);
}
