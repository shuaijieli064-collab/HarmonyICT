package com.nexus.common.result;

import java.time.Instant;

/**
 * 统一响应包装。
 * 为什么统一包装：前端（成员 C）按同一结构解析 code/message/data，避免裸对象导致契约漂移。
 *
 * @param <T> data 载荷类型
 */
public record Result<T>(int code, String message, T data, Instant timestamp) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, Instant.now());
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, Instant.now());
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null, Instant.now());
    }
}