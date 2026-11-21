package com.nexus.ai.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 模块自动配置桥接。
 * 为什么需要：主启动类在 com.nexus.api，默认组件扫描范围只有 com.nexus.api，
 * com.nexus.ai 下的 Provider/Demo 不会被加载。通过 AutoConfiguration.imports 注册本类，
 * 使 AI 模块不依赖 A 修改启动类即可生效；A 后续若把 scanBasePackages 调整为 com.nexus，
 * 可移除本桥接（重复扫描同名 bean 会被覆盖，不会重复实例化，但保持单一入口更清晰）。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nexus.ai")
public class AiAutoConfiguration {
}