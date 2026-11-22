package com.nexus.ingestion.storage;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 华为云 OBS 存储实现。配置 nexus.storage.type=obs 时激活（生产 profile）。
 * AK/SK 只走环境变量（规范 2.4），见 application-prod.yml 的 nexus.obs.* 占位符。
 * TODO(A, 阶段三): 预签名 URL 下载与生命周期策略
 */
@Component
@ConditionalOnProperty(name = "nexus.storage.type", havingValue = "obs")
public class ObsStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(ObsStorageProvider.class);

    private final ObsClient obsClient;
    private final String bucket;

    public ObsStorageProvider(@Value("${nexus.obs.endpoint}") String endpoint,
                              @Value("${nexus.obs.bucket}") String bucket,
                              @Value("${nexus.obs.access-key}") String accessKey,
                              @Value("${nexus.obs.secret-key}") String secretKey) {
        this.bucket = bucket;
        this.obsClient = new ObsClient(accessKey, secretKey, endpoint);
        log.info("OBS 存储已启用: endpoint={}, bucket={}", endpoint, bucket);
    }

    @Override
    public String providerName() {
        return "huawei-obs";
    }

    @Override
    public String store(Long userId, String fileName, byte[] content) {
        String ext = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') < 0
                ? 0 : fileName.lastIndexOf('.')).toLowerCase();
        String key = "%d/%s/%s%s".formatted(userId,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")), UUID.randomUUID(), ext);
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength((long) content.length);
            obsClient.putObject(bucket, key, in, metadata);
            return key;
        } catch (IOException e) {
            log.error("OBS 写入失败: key={}", key, e);
            throw new BizException(ResultCode.FILE_STORE_FAILED);
        }
    }

    @Override
    public byte[] load(Long userId, String storageKey) {
        if (!owns(userId, storageKey)) {
            throw new BizException(ResultCode.FILE_STORE_FAILED, "无权访问该文件");
        }
        try (var in = obsClient.getObject(bucket, storageKey).getObjectContent()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_STORE_FAILED, "文件读取失败");
        }
    }

    @Override
    public boolean owns(Long userId, String storageKey) {
        return storageKey.startsWith(userId + "/");
    }

    @PreDestroy
    public void close() {
        try {
            obsClient.close();
        } catch (IOException e) {
            log.warn("OBS 客户端关闭异常: {}", e.getMessage());
        }
    }
}