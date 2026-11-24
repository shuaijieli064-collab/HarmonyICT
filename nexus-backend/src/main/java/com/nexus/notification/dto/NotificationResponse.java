package com.nexus.notification.dto;

import com.nexus.notification.entity.Notification;

import java.time.Instant;

/**
 * 通知响应，契约见 api-specification.md（通知模块为体量扩充新增，字段与实体一一对应）。
 */
public record NotificationResponse(Long id, String category, String title, String content,
                                   String refType, Long refId, boolean read,
                                   Instant createdAt, Instant readAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getCategory(),
                notification.getTitle(), notification.getContent(), notification.getRefType(),
                notification.getRefId(), notification.getReadAt() != null,
                notification.getCreatedAt(), notification.getReadAt());
    }
}
