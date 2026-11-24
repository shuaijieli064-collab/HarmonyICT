package com.nexus.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.risk.dto.RiskDetailResponse;
import com.nexus.risk.dto.RiskResponse;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.risk.pipeline.RiskAnalysisPipelineService;
import com.nexus.risk.rule.RiskRuleEngine;
import com.nexus.risk.service.RiskService;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 风险服务实现。
 * acknowledge/resolve 做幂等处理：重复操作直接返回当前状态，避免前端重试报错。
 */
@Service
public class RiskServiceImpl implements RiskService {

    private final WorldRiskMapper riskMapper;
    private final WorldFactMapper factMapper;
    private final RiskRuleEngine ruleEngine;
    private final RiskAnalysisPipelineService analysisPipeline;
    private final com.nexus.risk.snapshot.RiskSnapshotService snapshotService;

    public RiskServiceImpl(WorldRiskMapper riskMapper, WorldFactMapper factMapper,
                           RiskRuleEngine ruleEngine, RiskAnalysisPipelineService analysisPipeline,
                           com.nexus.risk.snapshot.RiskSnapshotService snapshotService) {
        this.riskMapper = riskMapper;
        this.factMapper = factMapper;
        this.ruleEngine = ruleEngine;
        this.analysisPipeline = analysisPipeline;
        this.snapshotService = snapshotService;
    }

    @Override
    public PageResult<RiskResponse> list(Long userId, String level, String status, String riskType,
                                         int page, int pageSize) {
        String effectiveStatus = (status == null || status.isBlank()) ? "open" : status;
        LambdaQueryWrapper<WorldRisk> wrapper = new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, effectiveStatus)
                .eq(level != null && !level.isBlank(), WorldRisk::getLevel, level)
                .eq(riskType != null && !riskType.isBlank(), WorldRisk::getRiskType, riskType)
                .orderByDesc(WorldRisk::getCreatedAt);
        IPage<WorldRisk> result = riskMapper.selectPage(WorldPageHelper.clampPage(page, pageSize), wrapper);
        List<RiskResponse> records = result.getRecords().stream().map(RiskResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public RiskDetailResponse get(Long userId, Long id) {
        WorldRisk risk = requireRisk(userId, id);
        // 相关事实快照：风险关联实体名下的 active 事实，作为风险的证据支撑
        List<FactResponse> relatedFacts = risk.getEntityId() == null ? List.of()
                : factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                        .eq(WorldFact::getUserId, userId)
                        .eq(WorldFact::getSubjectId, risk.getEntityId())
                        .eq(WorldFact::getStatus, "active")
                        .orderByDesc(WorldFact::getCreatedAt))
                        .stream().map(FactResponse::from).toList();
        return new RiskDetailResponse(RiskResponse.from(risk), relatedFacts);
    }

    @Override
    public RiskResponse acknowledge(Long userId, Long id) {
        WorldRisk risk = requireRisk(userId, id);
        if (!"resolved".equals(risk.getStatus()) && !"acknowledged".equals(risk.getStatus())) {
            updateStatus(risk, "acknowledged");
        }
        return RiskResponse.from(requireRisk(userId, id));
    }

    @Override
    public RiskResponse resolve(Long userId, Long id) {
        WorldRisk risk = requireRisk(userId, id);
        if (!"resolved".equals(risk.getStatus())) {
            updateStatus(risk, "resolved");
        }
        return RiskResponse.from(requireRisk(userId, id));
    }

    private void updateStatus(WorldRisk risk, String status) {
        riskMapper.update(null, new LambdaUpdateWrapper<WorldRisk>()
                .eq(WorldRisk::getId, risk.getId())
                .set(WorldRisk::getStatus, status)
                .set(WorldRisk::getUpdatedAt, Instant.now()));
    }

    private WorldRisk requireRisk(Long userId, Long id) {
        WorldRisk risk = riskMapper.selectOne(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getId, id)
                .eq(WorldRisk::getUserId, userId));
        if (risk == null) {
            throw new BizException(ResultCode.RISK_NOT_FOUND);
        }
        return risk;
    }

    @Override
    public int scanNow() {
        // 同步触发两个既有引擎的扫描逻辑（红线不变：风险只由规则产生）
        int created = ruleEngine.scanNow() + analysisPipeline.scanNow();
        // 扫描后刷新今日风险快照（趋势图即时可见，同日 upsert 幂等）
        snapshotService.snapshotAll();
        return created;
    }

    @Override
    public java.util.List<com.nexus.risk.snapshot.RiskSnapshotService.TrendPoint> trend(Long userId, int days) {
        return snapshotService.trend(userId, days);
    }
}