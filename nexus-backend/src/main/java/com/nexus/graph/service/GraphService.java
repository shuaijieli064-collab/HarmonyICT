package com.nexus.graph.service;

import com.nexus.graph.dto.WorldGraphResponse;

/**
 * 世界模型图谱聚合服务（体量扩充第二波）。
 */
public interface GraphService {

    /**
     * 构建当前用户的图谱：节点=实体/事实/事件/未解决风险，
     * 边=事实归属（fact_of）、事件归属（event_of）、同源关联（co_source）、风险归属（risk_on）。
     *
     * @param userId 当前用户
     */
    WorldGraphResponse graph(Long userId);
}
