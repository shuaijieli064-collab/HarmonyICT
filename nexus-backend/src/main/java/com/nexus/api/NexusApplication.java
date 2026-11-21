package com.nexus.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NEXUS 后端启动入口。
 * 为什么单独放在 api 包：启动类与接口入口属于应用层，业务模块（auth/world/ai 等）保持解耦，便于按目录边界分工。
 */
@SpringBootApplication
@MapperScan("com.nexus.**.mapper")
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }
}