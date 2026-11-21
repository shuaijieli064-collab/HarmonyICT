package com.nexus.common.result;

import java.util.List;

/**
 * 统一分页响应，与 api-specification.md 第 1 节契约一致。
 * 为什么独立封装：MyBatis-Plus 的 Page 对象包含大量实现字段，直接外泄会造成契约漂移。
 *
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, long page, long pageSize) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long pageSize) {
        return new PageResult<>(records, total, page, pageSize);
    }
}