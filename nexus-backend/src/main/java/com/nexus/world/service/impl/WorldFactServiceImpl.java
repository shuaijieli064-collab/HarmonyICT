package com.nexus.world.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.dto.FactUpdateRequest;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import com.nexus.world.service.WorldFactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 事实服务实现。
 * 链式失效：PUT 更新时旧行置 superseded 并封口 validUntil，新行落库——世界模型保留完整历史轨迹。
 */
@Service
public class WorldFactServiceImpl implements WorldFactService {

    private final WorldFactMapper factMapper;
    private final WorldEntityMapper entityMapper;

    public WorldFactServiceImpl(WorldFactMapper factMapper, WorldEntityMapper entityMapper) {
        this.factMapper = factMapper;
        this.entityMapper = entityMapper;
    }

    @Override
    public FactResponse create(Long userId, FactCreateRequest request) {
        requireSubject(userId, request.subjectId());
        Instant now = Instant.now();
        // P1-5 拍板（联调 2026-11-22）：同源同主体同谓词视为同一事实版本（uk_wf_src_fact 唯一索引），
        // 重抽写入前先封口旧 active 行走链式失效，upsert 语义不触发唯一冲突；人工录入（sourceId 为空）不受影响
        if (request.sourceId() != null) {
            supersedeActive(userId, request.subjectId(), request.predicate(), request.sourceId(), now);
        }
        WorldFact fact = new WorldFact();
        fact.setUserId(userId);
        fact.setSubjectId(request.subjectId());
        fact.setPredicate(request.predicate());
        fact.setObjectValue(request.objectValue());
        // PR #1 review 拍板补列：人工录入默认 string/confirmed；抽取写入由 B 显式传入
        fact.setValueType(request.valueType() == null ? "string" : request.valueType());
        fact.setUnit(request.unit());
        fact.setNormalizedValue(request.normalizedValue());
        fact.setEvidence(request.evidence());
        fact.setSourceId(request.sourceId());
        // P0 拍板（联调 2026-11-22）：抽取写入显式携带 confidence/factStatus，人工录入缺省兜底 1.0/confirmed
        fact.setConfidence(request.confidence() == null ? BigDecimal.ONE : request.confidence());
        fact.setValidFrom(request.validFrom() == null ? now : request.validFrom());
        // 证书类事实的到期时间（CERT_EXPIRY_30D 规则依赖），人工登记与抽取写入均走此字段
        fact.setValidUntil(request.validUntil());
        fact.setStatus("active");
        fact.setFactStatus(request.factStatus() == null ? "confirmed" : request.factStatus());
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        factMapper.insert(fact);
        return FactResponse.from(fact);
    }

    @Override
    public FactResponse get(Long userId, Long id) {
        return FactResponse.from(requireFact(userId, id));
    }

    @Override
    public PageResult<FactResponse> list(Long userId, Long subjectId, String predicate, Long sourceId,
                                         String status, int page, int pageSize) {
        String effectiveStatus = (status == null || status.isBlank()) ? "active" : status;
        LambdaQueryWrapper<WorldFact> wrapper = new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, effectiveStatus)
                .eq(subjectId != null, WorldFact::getSubjectId, subjectId)
                .eq(predicate != null && !predicate.isBlank(), WorldFact::getPredicate, predicate)
                // sourceId 是抽取结果轮询入口（契约 4.2），B 后台抽取完成后前端按此轮询
                .eq(sourceId != null, WorldFact::getSourceId, sourceId)
                .orderByDesc(WorldFact::getCreatedAt);
        IPage<WorldFact> result = factMapper.selectPage(WorldEntityServiceImpl.clampPage(page, pageSize), wrapper);
        List<FactResponse> records = result.getRecords().stream().map(FactResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public FactResponse update(Long userId, Long id, FactUpdateRequest request) {
        WorldFact old = requireFact(userId, id);
        if (!"active".equals(old.getStatus())) {
            // 只有 active 事实允许链式更新，superseded/retracted 属历史数据，改动会破坏可追溯性
            throw new BizException(ResultCode.WORLD_FACT_CONFLICT);
        }
        Instant now = Instant.now();
        old.setStatus("superseded");
        old.setValidUntil(now);
        old.setUpdatedAt(now);
        factMapper.updateById(old);

        WorldFact next = new WorldFact();
        next.setUserId(userId);
        next.setSubjectId(old.getSubjectId());
        next.setPredicate(old.getPredicate());
        next.setObjectValue(request.objectValue());
        // 类型/单位/归一化继承旧事实；人工更新的宾值无原文证据，evidence 置空、状态置 confirmed
        next.setValueType(old.getValueType());
        next.setUnit(old.getUnit());
        next.setNormalizedValue(old.getNormalizedValue());
        // sourceId 置空：新事实是用户/Agent 修正版而非原始抽取结果（人工语义），
        // 同时规避 uk_wf_src_fact 唯一索引——旧行 (user,source,subject,predicate) 在同事务仍占位，
        // 新行继承 sourceId 会立即撞索引（H2 立即检查、PG 同样冲突），schema.sql 注释的人工语义正好豁免
        next.setSourceId(null);
        next.setConfidence(old.getConfidence());
        next.setValidFrom(now);
        next.setStatus("active");
        next.setFactStatus("confirmed");
        next.setCreatedAt(now);
        next.setUpdatedAt(now);
        factMapper.insert(next);
        return FactResponse.from(next);
    }

    @Override
    public void delete(Long userId, Long id) {
        WorldFact fact = requireFact(userId, id);
        fact.setStatus("retracted");
        fact.setUpdatedAt(Instant.now());
        factMapper.updateById(fact);
    }

    private WorldFact requireFact(Long userId, Long id) {
        WorldFact fact = factMapper.selectOne(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getId, id)
                .eq(WorldFact::getUserId, userId));
        if (fact == null) {
            throw new BizException(ResultCode.WORLD_FACT_NOT_FOUND);
        }
        return fact;
    }

    private void requireSubject(Long userId, Long subjectId) {
        Long count = entityMapper.selectCount(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getId, subjectId)
                .eq(WorldEntity::getUserId, userId)
                .eq(WorldEntity::getStatus, 1));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.WORLD_ENTITY_NOT_FOUND);
        }
    }

    /** 供 ingestion 等后续模块批量封口使用（预留）。
     * B review 拍板（2026-11-22）：封口条件必须含 source_id，与 uk_wf_src_fact 唯一键完全对齐——
     * 否则同主体同谓词、来自不同来源的 active 事实会被误封口（唯一键允许多源并存，封口只应作用于同源重抽） */
    void supersedeActive(Long userId, Long subjectId, String predicate, Long sourceId, Instant until) {
        factMapper.update(null, new LambdaUpdateWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getSubjectId, subjectId)
                .eq(WorldFact::getPredicate, predicate)
                .eq(WorldFact::getSourceId, sourceId)
                .eq(WorldFact::getStatus, "active")
                .set(WorldFact::getStatus, "superseded")
                .set(WorldFact::getValidUntil, until)
                .set(WorldFact::getUpdatedAt, until));
    }
}