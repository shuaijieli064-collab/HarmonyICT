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
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云资源连通性验证（RDS / Redis / OBS）。
 * 为什么在启动后自动跑一次：阶段一要求"配置模板 + 连通性验证方式"，把结果直接落日志，避免逐个手工探活。
 * OBS 探活按需执行：仅 nexus.storage.type=obs 且 AK/SK 已配置时才建连（headBucket），
 * 临时 ObsClient 用完即关，不与 ObsStorageProvider 共享连接。
 */
@Component
public class CloudConnectChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudConnectChecker.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String storageType;
    private final String obsEndpoint;
    private final String obsBucket;
    private final String obsAccessKey;
    private final String obsSecretKey;

    public CloudConnectChecker(DataSource dataSource,
                               RedisConnectionFactory redisConnectionFactory,
                               @Value("${nexus.storage.type:local}") String storageType,
                               @Value("${nexus.obs.endpoint:}") String obsEndpoint,
                               @Value("${nexus.obs.bucket:}") String obsBucket,
                               @Value("${nexus.obs.access-key:}") String obsAccessKey,
                               @Value("${nexus.obs.secret-key:}") String obsSecretKey) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.storageType = storageType;
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

        status.put("obs", checkObs());

        status.forEach((k, v) -> {
            if (v.startsWith("OK")) {
                log.info("云资源连通性 [{}]: {}", k, v);
            } else {
                log.warn("云资源连通性 [{}]: {}", k, v);
            }
        });
    }

    /**
     * OBS 探活：本地 local 存储跳过；凭据未注入时标记 PENDING（等券阶段属预期）；
     * 已配置则 headBucket 验证桶可达与 AK/SK 有效性。
     */
    private String checkObs() {
        if (!"obs".equals(storageType)) {
            return "SKIPPED: 本地存储模式未启用 OBS";
        }
        if (isBlank(obsEndpoint) || isBlank(obsBucket) || isBlank(obsAccessKey) || isBlank(obsSecretKey)) {
            return "PENDING: 缺少 NEXUS_OBS_ENDPOINT/BUCKET/AK/SK 配置";
        }
        try (ObsClient obsClient = new ObsClient(obsAccessKey, obsSecretKey, obsEndpoint)) {
            boolean bucketExists = obsClient.headBucket(obsBucket);
            return bucketExists
                    ? "OK: bucket=" + obsBucket
                    : "FAIL: bucket=" + obsBucket + " 不存在或无访问权限";
        } catch (Exception e) {
            return "FAIL: " + e.getMessage();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
