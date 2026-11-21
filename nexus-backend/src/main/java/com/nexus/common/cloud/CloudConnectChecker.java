package com.nexus.common.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云资源连通性验证（RDS / Redis）。
 * 为什么在启动后自动跑一次：阶段一要求"配置模板 + 连通性验证方式"，把结果直接落日志，避免逐个手工探活。
 * OBS 属于 HTTP 探活且依赖 OBS SDK（阶段二接入），见 TODO(A, 阶段二)。
 */
@Component
public class CloudConnectChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudConnectChecker.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public CloudConnectChecker(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> status = new LinkedHashMap<>();

        try (Connection ignored = dataSource.getConnection()) {
            status.put("rds", "OK: " + dataSource.getConnection().getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            status.put("rds", "FAIL: " + e.getMessage());
        }

        try {
            redisConnectionFactory.getConnection().ping();
            status.put("redis", "OK");
        } catch (Exception e) {
            // 本地无 Redis 属预期（弱依赖），降级为 WARN 不阻断启动
            status.put("redis", "DEGRADED: " + e.getMessage());
        }

        status.forEach((k, v) -> {
            if (v.startsWith("OK")) {
                log.info("云资源连通性 [{}]: {}", k, v);
            } else {
                log.warn("云资源连通性 [{}]: {}", k, v);
            }
        });
    }
}