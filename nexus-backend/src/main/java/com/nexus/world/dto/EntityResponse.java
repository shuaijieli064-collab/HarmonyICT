package com.nexus.world.dto;

import com.nexus.world.entity.WorldEntity;

import java.time.Instant;

/**
 * 实体响应，字段与 api-specification.md 5.1 节契约一致（BIGINT 由 JacksonConfig 序列化为字符串）。
 */
public record EntityResponse(Long id, String entityType, String name, String attributes,
                             Instant createdAt, Instant updatedAt) {

    public static EntityResponse from(WorldEntity entity) {
        return new EntityResponse(entity.getId(), entity.getEntityType(), entity.getName(),
                entity.getAttributes(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}