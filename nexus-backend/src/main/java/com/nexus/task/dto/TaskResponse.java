package com.nexus.task.dto;

import com.nexus.task.entity.Task;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 任务响应，字段与 api-specification.md 7 节契约一致。
 */
public record TaskResponse(Long id, String title, String description, String status, Integer priority,
                           LocalDate dueDate, String sourceType, Long riskId, Instant createdAt, Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getDueDate(), task.getSourceType(), task.getRiskId(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}