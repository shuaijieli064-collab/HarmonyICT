package com.nexus.world.dto;

import com.nexus.world.entity.WorldSource;

import java.time.Instant;

/**
 * 来源响应，字段与 api-specification.md 4 节契约一致。
 */
public record SourceResponse(Long sourceId, String fileName, String mimeType, Long fileSize,
                             String status, Instant createdAt) {

    public static SourceResponse from(WorldSource source) {
        return new SourceResponse(source.getId(), source.getFileName(), source.getMimeType(),
                source.getFileSize(), source.getStatus(), source.getCreatedAt());
    }
}