package com.nexus.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NEXUS 后端启动入口。
 * 为什么显式指定 scanBasePackages：启动类位于 api 包，业务模块在 com.nexus 下平级分包，
 * 不显式扩大扫描范围会导致 auth/common 等模块的 Bean 无法注册。
 * EnableScheduling：风险规则引擎定时扫描；EnableAsync：Agent 编排异步执行（契约 8 节）。
 */
@SpringBootApplication(scanBasePackages = "com.nexus")
@MapperScan("com.nexus.**.mapper")
@EnableScheduling
@EnableAsync
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }
}