package com.nexus.ai.extraction;

/**
 * 抽取结果不符合 docs/extraction-schema.md 时抛出。
 * 为什么单独定义：Provider 只对「结构不合法」做重试，不对网络/鉴权失败重试，避免重复计费。
 */
public class ExtractionSchemaException extends RuntimeException {

    public ExtractionSchemaException(String message) {
        super(message);
    }
}