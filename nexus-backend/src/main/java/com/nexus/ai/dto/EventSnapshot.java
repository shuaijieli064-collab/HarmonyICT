package com.nexus.ai.dto;

import java.time.Instant;

/**
 * 事件快照（world_events 只读投影，A 侧查询后传入）。
 *
 * @param id          world_events.id
 * @param entityId    关联实体 id（可为 null）
 * @param eventType   education / career / asset / health / contract / other
 * @param title       事件标题
 * @param description 事件描述（可为 null）
 * @param occurredAt  发生时间（可为 null）
 */
public record EventSnapshot(Long id, Long entityId, String eventType, String title,
                            String description, Instant occurredAt) {
}