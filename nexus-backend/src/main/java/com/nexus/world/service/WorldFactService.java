package com.nexus.world.service;

import com.nexus.common.result.PageResult;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.dto.FactUpdateRequest;

/**
 * 事实服务。事实时效语义（链式失效）见 docs/database-design.md 第 5 节。
 */
public interface WorldFactService {

    /** 创建事实，subjectId 须属于当前用户；人工录入默认 confidence=1.0（AI 抽取写入时显式传置信度） */
    FactResponse create(Long userId, FactCreateRequest request);

    /** 查询详情 */
    FactResponse get(Long userId, Long id);

    /** 分页查询：status 缺省 active；subjectId/predicate/sourceId 可选过滤，sourceId 是抽取结果轮询入口（契约 4.2） */
    PageResult<FactResponse> list(Long userId, Long subjectId, String predicate, Long sourceId, String status,
                                  int page, int pageSize);

    /**
     * 更新宾值，走链式失效：旧事实置 superseded 并封口 validUntil，新事实落库，历史可追溯。
     *
     * @return 新事实
     */
    FactResponse update(Long userId, Long id, FactUpdateRequest request);

    /** 撤回：status 置 retracted */
    void delete(Long userId, Long id);
}