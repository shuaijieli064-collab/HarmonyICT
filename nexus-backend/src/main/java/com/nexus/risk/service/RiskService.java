package com.nexus.risk.service;

import com.nexus.common.result.PageResult;
import com.nexus.risk.dto.RiskDetailResponse;
import com.nexus.risk.dto.RiskResponse;

/**
 * 风险服务：查询与状态流转。风险的产生只走 RiskRuleEngine，本服务不提供创建入口。
 */
public interface RiskService {

    /** 分页查询：level/status/riskType 可选过滤，status 缺省 open */
    PageResult<RiskResponse> list(Long userId, String level, String status, String riskType,
                                  int page, int pageSize);

    /** 详情：含相关事实快照（按 entity_id 关联 active 事实） */
    RiskDetailResponse get(Long userId, Long id);

    /** 确认风险：status → acknowledged；已确认幂等返回成功 */
    RiskResponse acknowledge(Long userId, Long id);

    /** 解决风险：status → resolved；幂等返回成功 */
    RiskResponse resolve(Long userId, Long id);
}