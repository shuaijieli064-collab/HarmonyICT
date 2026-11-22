package com.nexus.world.dto;

import com.nexus.world.entity.WorldEvent;

import java.time.Instant;

/**
 * 事件响应，字段与 api-specification.md 5.3 节契约一致。
 */
public record EventResponse(Long id, Long entityId, String eventType, String title, String description,
                            Instant occurredAt, Long sourceId, Instant createdAt, Instant updatedAt) {

    public static EventResponse from(WorldEvent event) {
        return new EventResponse(event.getId(), event.getEntityId(), event.getEventType(), event.getTitle(),
                event.getDescription(), event.getOccurredAt(), event.getSourceId(),
                event.getCreatedAt(), event.getUpdatedAt());
    }
}