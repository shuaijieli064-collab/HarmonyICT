package com.nexus.world.service;

import com.nexus.common.result.PageResult;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.EventResponse;

/**
 * 事件服务。
 */
public interface WorldEventService {

    /** 创建事件，entityId/sourceId 可选，传入时校验归属 */
    EventResponse create(Long userId, EventCreateRequest request);

    /** 查询详情 */
    EventResponse get(Long userId, Long id);

    /** 分页查询：按 occurredAt 倒序，eventType/entityId 可选过滤 */
    PageResult<EventResponse> list(Long userId, String eventType, Long entityId, int page, int pageSize);
}