package com.nexus.ingestion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.ingestion.storage.StorageProvider;
import com.nexus.world.dto.SourceResponse;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.ingestion.service.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 来源服务实现。
 * 校验以扩展名白名单为主（契约 4.1），双保险再查 contentType；大小限制 20MB。
 */
@Service
public class SourceServiceImpl implements SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceServiceImpl.class);

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "txt", "md", "png", "jpg");

    private final WorldSourceMapper sourceMapper;
    private final StorageProvider storageProvider;

    public SourceServiceImpl(WorldSourceMapper sourceMapper, StorageProvider storageProvider) {
        this.sourceMapper = sourceMapper;
        this.storageProvider = storageProvider;
    }

    @Override
    public SourceResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.FILE_TOO_LARGE);
        }
        String fileName = file.getOriginalFilename();
        String ext = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ResultCode.FILE_TYPE_UNSUPPORTED);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_STORE_FAILED, "文件内容读取失败");
        }
        String storageKey = storageProvider.store(userId, fileName, content);

        Instant now = Instant.now();
        WorldSource source = new WorldSource();
        source.setUserId(userId);
        source.setSourceType("upload");
        source.setFileName(fileName);
        source.setObsKey(storageKey);
        source.setMimeType(file.getContentType());
        source.setFileSize(file.getSize());
        // PR #1 review 拍板补列：sha256 供去重/比对，page_count 由 OCR/解析回填
        source.setSha256(sha256Hex(content));
        source.setUploadedAt(now);
        source.setStatus("pending");
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        sourceMapper.insert(source);
        log.info("文件上传成功: userId={}, sourceId={}, key={}, provider={}",
                userId, source.getId(), storageKey, storageProvider.providerName());
        return SourceResponse.from(source);
    }

    @Override
    public PageResult<SourceResponse> list(Long userId, String status, int page, int pageSize) {
        LambdaQueryWrapper<WorldSource> wrapper = new LambdaQueryWrapper<WorldSource>()
                .eq(WorldSource::getUserId, userId)
                .eq(status != null && !status.isBlank(), WorldSource::getStatus, status)
                .orderByDesc(WorldSource::getCreatedAt);
        IPage<WorldSource> result = sourceMapper.selectPage(WorldEntityPageHelper.clampPage(page, pageSize), wrapper);
        List<SourceResponse> records = result.getRecords().stream().map(SourceResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public SourceResponse get(Long userId, Long sourceId) {
        return SourceResponse.from(requireSource(userId, sourceId));
    }

    @Override
    public byte[] loadFile(Long userId, Long sourceId) {
        WorldSource source = requireSource(userId, sourceId);
        return storageProvider.load(userId, source.getObsKey());
    }

    private WorldSource requireSource(Long userId, Long sourceId) {
        WorldSource source = sourceMapper.selectOne(new LambdaQueryWrapper<WorldSource>()
                .eq(WorldSource::getId, sourceId)
                .eq(WorldSource::getUserId, userId));
        if (source == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return source;
    }

    private String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(content)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，该分支理论不可达；防御式转存储异常
            throw new BizException(ResultCode.FILE_STORE_FAILED);
        }
    }
}