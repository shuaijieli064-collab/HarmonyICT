package com.nexus.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NEXUS 后端启动入口。
 * 为什么显式指定 scanBasePackages：启动类位于 api 包，业务模块在 com.nexus 下平级分包，
 * 不显式扩大扫描范围会导致 auth/common 等模块的 Bean 无法注册。
 */
@SpringBootApplication(scanBasePackages = "com.nexus")
@MapperScan("com.nexus.**.mapper")
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }
}