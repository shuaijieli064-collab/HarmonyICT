package com.nexus.world.service;

import com.nexus.common.result.PageResult;
import com.nexus.world.dto.EntityCreateRequest;
import com.nexus.world.dto.EntityResponse;
import com.nexus.world.dto.EntityUpdateRequest;

/**
 * 世界实体服务。user_id 一律由调用方从 UserContext 取得传入，服务层不再二次解析。
 */
public interface WorldEntityService {

    /** 创建实体，attributes 须为合法 JSON 字符串 */
    EntityResponse create(Long userId, EntityCreateRequest request);

    /** 查询详情，不存在或已删除抛 WORLD_ENTITY_NOT_FOUND */
    EntityResponse get(Long userId, Long id);

    /** 分页查询：entityType 精确过滤、keyword 按 name 模糊匹配 */
    PageResult<EntityResponse> list(Long userId, String entityType, String keyword, int page, int pageSize);

    /** 更新 name/attributes */
    EntityResponse update(Long userId, Long id, EntityUpdateRequest request);

    /**
     * 逻辑删除实体（status=0），并同步撤回其名下 active 事实（retracted），
     * 避免"实体已删但事实仍可查"的悬挂数据。
     */
    void delete(Long userId, Long id);
}