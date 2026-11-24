package com.nexus.asset.dto;

import com.nexus.world.entity.WorldEntity;

import java.time.Instant;
import java.util.List;

/**
 * 资产条目响应：世界实体 + 其 active 事实聚合。
 * attributes 为 JSON 字符串，前端按契约解析（同实体接口约定）。
 */
public record AssetResponse(Long id, String entityType, String name, String attributes,
                            Instant createdAt, List<AssetFactResponse> facts) {

    public static AssetResponse from(WorldEntity entity, List<AssetFactResponse> facts) {
        return new AssetResponse(entity.getId(), entity.getEntityType(), entity.getName(),
                entity.getAttributes(), entity.getCreatedAt(), facts);
    }
}
