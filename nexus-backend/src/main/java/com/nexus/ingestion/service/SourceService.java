package com.nexus.ingestion.service;

import com.nexus.common.result.PageResult;
import com.nexus.world.dto.SourceResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资料来源服务：上传校验 → 存储 → world_sources 落库。
 * 状态流转由本服务负责（pending → parsing → parsed/failed），AI 抽取（B）只读该状态。
 */
public interface SourceService {

    /** 上传文件：校验类型与大小（契约 4.1），存储后落库，初始状态 pending（等待抽取调度） */
    SourceResponse upload(Long userId, MultipartFile file);

    /** 分页查询来源，status 可选过滤 */
    PageResult<SourceResponse> list(Long userId, String status, int page, int pageSize);

    /** 来源详情 */
    SourceResponse get(Long userId, Long sourceId);

    /** 按存储键读取文件内容（下载前校验归属，防跨用户读文件） */
    byte[] loadFile(Long userId, Long sourceId);
}