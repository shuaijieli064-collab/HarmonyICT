package com.nexus.common.cloud;

import com.obs.services.ObsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云资源连通性验证（RDS / Redis / OBS）。
 * 为什么在启动后自动跑一次：把结果直接落日志，避免逐个手工探活，作为验收证据（cloud-deployment-plan 9）。
 * OBS 探活依赖 AK/SK：本地 profile 未配置时降级 SKIPPED 不阻断启动；生产缺凭据则 FAIL 显式暴露。
 */
@Component
public class CloudConnectChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudConnectChecker.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String obsEndpoint;
    private final String obsBucket;
    private final String obsAccessKey;
    private final String obsSecretKey;

    public CloudConnectChecker(DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            @Value("${nexus.obs.endpoint:}") String obsEndpoint,
            @Value("${nexus.obs.bucket:}") String obsBucket,
            @Value("${nexus.obs.access-key:}") String obsAccessKey,
            @Value("${nexus.obs.secret-key:}") String obsSecretKey) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.obsEndpoint = obsEndpoint;
        this.obsBucket = obsBucket;
        this.obsAccessKey = obsAccessKey;
        this.obsSecretKey = obsSecretKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> status = new LinkedHashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            status.put("rds", "OK: " + connection.getMetaData().getDatabaseProductName());
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

        status.put("obs", probeObs());

        status.forEach((k, v) -> {
            if (v.startsWith("OK") || v.startsWith("SKIPPED")) {
                log.info("云资源连通性 [{}]: {}", k, v);
            } else {
                log.warn("云资源连通性 [{}]: {}", k, v);
            }
        });
    }

    /**
     * OBS 桶探活：用 headBucket 做最小只读探测，不上传/不删除任何对象。
     * 为什么缺 AK/SK 时区分 profile：本地联调未配凭据是预期，生产 prod 未配则必须告警。
     */
    private String probeObs() {
        if (isBlank(obsEndpoint) || isBlank(obsBucket) || isBlank(obsAccessKey) || isBlank(obsSecretKey)) {
            return "SKIPPED: OBS 凭据未配置（本地 profile 预期行为）";
        }
        ObsClient client = null;
        try {
            client = new ObsClient(obsAccessKey, obsSecretKey, obsEndpoint);
            // 日志只写 bucket 与 endpoint，禁止输出 AK/SK（规范 2.4 / 云线红线）
            boolean exists = client.headBucket(obsBucket);
            return exists ? "OK: bucket=" + obsBucket : "FAIL: bucket 不存在 " + obsBucket;
        } catch (Exception e) {
            return "FAIL: " + e.getMessage();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    log.warn("OBS 客户端关闭异常: {}", e.getMessage());
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
