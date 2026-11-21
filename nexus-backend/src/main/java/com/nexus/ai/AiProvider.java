package com.nexus.ai;

/**
 * AI 能力顶层接口。成员 B 在此包内提供实现，A 只定义签名（目录边界约定）。
 * 为什么业务层只依赖接口：规范 6 节要求隔离具体模型 SDK，便于替换供应商与评测。
 */
public interface AiProvider {

    /** 供应商标识，如 huawei-pangu / openai-compatible */
    String providerName();

    /** 健康探测，用于连通性验证与降级判断 */
    boolean isAvailable();
}