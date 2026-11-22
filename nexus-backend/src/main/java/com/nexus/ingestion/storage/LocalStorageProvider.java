package com.nexus.ingestion.storage;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储实现（默认激活）。
 * 为什么默认本地：阶段二本地联调无 OBS 凭证；生产将 nexus.storage.type 置为 obs 切换华为云 OBS。
 * key 格式 {userId}/{yyyyMM}/{uuid}.{ext}，userId 前缀天然实现用户间隔离。
 */
@Component
@ConditionalOnProperty(name = "nexus.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final Path baseDir;

    // 默认落系统临时目录：部分 Windows 环境对用户主目录建目录有策略限制（实测 C:\Users\<user> 下 AccessDenied），
    // java.io.tmpdir 一定可写；需要持久化时用 NEXUS_STORAGE_DIR 覆盖
    public LocalStorageProvider(@Value("${nexus.storage.local-dir:${java.io.tmpdir}/nexus-uploads}") String localDir) {
        this.baseDir = Path.of(localDir);
        log.info("本地存储已启用，根目录: {}", baseDir);
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public String store(Long userId, String fileName, byte[] content) {
        String ext = extensionOf(fileName);
        String key = "%d/%s/%s%s".formatted(userId,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")), UUID.randomUUID(), ext);
        try {
            Path target = baseDir.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            log.error("本地存储写入失败: key={}", key, e);
            throw new BizException(ResultCode.FILE_STORE_FAILED);
        }
    }

    @Override
    public byte[] load(Long userId, String storageKey) {
        if (!owns(userId, storageKey)) {
            throw new BizException(ResultCode.FILE_STORE_FAILED, "无权访问该文件");
        }
        try {
            return Files.readAllBytes(baseDir.resolve(storageKey));
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_STORE_FAILED, "文件读取失败");
        }
    }

    @Override
    public boolean owns(Long userId, String storageKey) {
        // key 以 {userId}/ 开头即归属；同时归一化校验防路径穿越（规范 2.4）
        Path resolved = baseDir.resolve(storageKey).normalize();
        return storageKey.startsWith(userId + "/") && resolved.startsWith(baseDir) && Files.exists(resolved);
    }

    private String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase();
    }
}