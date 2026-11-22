package com.nexus.risk.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.RiskAnalysisProvider;
import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 风险分析管线（阶段四）：接入 B 的 RiskAnalysisProvider 作为生产风险源。
 * 与既有 RiskRuleEngine（单事实定时规则）并存，幂等键统一为 (userId, entityId, ruleCode)，
 * 同码风险不重复产生（如 B 的 cert_expiry 与引擎 CERT_EXPIRY_30D 重码时只落一条）。
 * 红线：风险结论只由规则产生（rule_code 可追溯），模型只做解释（规范第 6 节）；
 * 落库后同步写 world_risk_facts 证据关联（P1-6 拍板），evidenceFactIds 为空时跳过。
 */
@Component
public class RiskAnalysisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisPipelineService.class);

    private final WorldFactMapper factMapper;
    private final WorldEntityMapper entityMapper;
    private final WorldEventMapper eventMapper;
    private final WorldRiskMapper riskMapper;
    private final WorldRiskFactMapper riskFactMapper;
    private final RiskAnalysisProvider riskAnalysisProvider;

    public RiskAnalysisPipelineService(WorldFactMapper factMapper, WorldEntityMapper entityMapper,
                                       WorldEventMapper eventMapper, WorldRiskMapper riskMapper,
                                       WorldRiskFactMapper riskFactMapper, RiskAnalysisProvider riskAnalysisProvider) {
        this.factMapper = factMapper;
        this.entityMapper = entityMapper;
        this.eventMapper = eventMapper;
        this.riskMapper = riskMapper;
        this.riskFactMapper = riskFactMapper;
        this.riskAnalysisProvider = riskAnalysisProvider;
    }

    /** 系统级定时扫描：与 RiskRuleEngine 同频，按用户分组构建快照后交 Provider 分析 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scan() {
        try {
            List<WorldFact> activeFacts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                    .eq(WorldFact::getStatus, "active"));
            if (activeFacts.isEmpty()) {
                return;
            }
            int created = 0;
            for (Long userId : activeFacts.stream().map(WorldFact::getUserId).distinct().toList()) {
                created += analyzeUser(userId, activeFacts);
            }
            if (created > 0) {
                log.info("风险分析管线本轮产生 {} 条风险", created);
            }
        } catch (Exception e) {
            // 后台任务不允许中断调度，吞异常记日志
            log.error("风险分析管线扫描异常", e);
        }
    }

    private int analyzeUser(Long userId, List<WorldFact> allActiveFacts) {
        List<WorldFact> userFacts = allActiveFacts.stream()
                .filter(f -> f.getUserId().equals(userId))
                .toList();
        List<Long> subjectIds = userFacts.stream().map(WorldFact::getSubjectId).distinct().toList();
        Map<Long, WorldEntity> entityById = subjectIds.isEmpty() ? Map.of()
                : entityMapper.selectBatchIds(subjectIds).stream()
                        .collect(Collectors.toMap(WorldEntity::getId, Function.identity()));

        RiskAnalysisRequest request = new RiskAnalysisRequest(
                userFacts.stream().map(fact -> toFactSnapshot(fact, entityById)).toList(),
                toEventSnapshots(userId),
                Instant.now());

        RiskAnalysisRequest.RiskAnalysisResult result = riskAnalysisProvider.analyze(request);
        if (result == null || result.risks() == null) {
            return 0;
        }
        int created = 0;
        for (RiskAnalysisRequest.DetectedRisk risk : result.risks()) {
            created += createIfAbsent(userId, risk);
        }
        return created;
    }

    private List<EventSnapshot> toEventSnapshots(Long userId) {
        return eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                        .eq(WorldEvent::getUserId, userId)).stream()
                .map(event -> new EventSnapshot(event.getId(), event.getEntityId(), event.getEventType(),
                        event.getTitle(), event.getDescription(), event.getOccurredAt()))
                .toList();
    }

    private FactSnapshot toFactSnapshot(WorldFact fact, Map<Long, WorldEntity> entityById) {
        WorldEntity subject = entityById.get(fact.getSubjectId());
        return new FactSnapshot(
                fact.getId(),
                fact.getSubjectId(),
                subject == null ? "event" : subject.getEntityType(),
                subject == null ? "未知主体" : subject.getName(),
                fact.getPredicate(),
                fact.getObjectValue(),
                fact.getConfidence() == null ? null : fact.getConfidence().doubleValue(),
                fact.getValidFrom(),
                fact.getValidUntil());
    }

    private int createIfAbsent(Long userId, RiskAnalysisRequest.DetectedRisk detected) {
        if (detected.entityId() == null) {
            // 幂等键依赖 entityId 定位实体，无实体归属的候选风险丢弃并记日志（可追溯性不足不入库）
            log.warn("风险候选缺少 entityId，丢弃: ruleCode={} title={}", detected.ruleCode(), detected.title());
            return 0;
        }
        Long exists = riskMapper.selectCount(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getEntityId, detected.entityId())
                .eq(WorldRisk::getRuleCode, detected.ruleCode())
                .ne(WorldRisk::getStatus, "resolved"));
        if (exists != null && exists > 0) {
            return 0;
        }
        Instant now = Instant.now();
        WorldRisk risk = new WorldRisk();
        risk.setUserId(userId);
        risk.setRiskType(detected.riskType());
        risk.setLevel(detected.level());
        risk.setTitle(detected.title());
        risk.setDescription(detected.description());
        risk.setEntityId(detected.entityId());
        risk.setRuleCode(detected.ruleCode());
        risk.setStatus("open");
        risk.setCreatedAt(now);
        risk.setUpdatedAt(now);
        riskMapper.insert(risk);

        List<Long> evidenceFactIds = detected.evidenceFactIds();
        if (evidenceFactIds != null) {
            for (Long factId : evidenceFactIds) {
                riskFactMapper.insert(new WorldRiskFact(risk.getId(), factId));
            }
        }
        return 1;
    }
}