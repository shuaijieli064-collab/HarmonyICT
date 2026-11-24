package com.nexus.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.entity.WorldRiskFact;
import com.nexus.risk.mapper.WorldRiskFactMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldFactMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 最小可用风险规则引擎（阶段三）。
 * 红线：风险结论只由规则产生并带 rule_code 可追溯，模型（成员 B）只做解释（规范第 6 节）。
 * 幂等：同一 (rule_code, entityId) 存在未解决风险时不重复产生。
 *
 * 当前登记规则：
 * 1. CERT_EXPIRY_30D —— 证书类事实（predicate 含 certificate）valid_until 30 天内到期 → high
 * 2. DATA_STALENESS_180D —— 事实创建超过 180 天仍 active → low（数据陈旧提醒）
 *
 * TODO(A, 阶段四): 规则清单外置为配置表，支持规则增删不发布代码
 */
@Component
public class RiskRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleEngine.class);

    private static final Duration CERT_EXPIRY_WINDOW = Duration.ofDays(30);
    private static final Duration STALENESS_THRESHOLD = Duration.ofDays(180);

    private final WorldFactMapper factMapper;
    private final WorldRiskMapper riskMapper;
    private final WorldRiskFactMapper riskFactMapper;

    public RiskRuleEngine(WorldFactMapper factMapper, WorldRiskMapper riskMapper,
                          WorldRiskFactMapper riskFactMapper) {
        this.factMapper = factMapper;
        this.riskMapper = riskMapper;
        this.riskFactMapper = riskFactMapper;
    }

    /**
     * 定时扫描全量 active 事实（后台任务不允许中断调度，异常吞掉记日志）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void scheduledScan() {
        try {
            scanNow();
        } catch (Exception e) {
            log.error("规则引擎扫描异常", e);
        }
    }

    /** 手动/同步触发扫描（演示与联调用），返回本轮新增风险数 */
    public int scanNow() {
        List<WorldFact> activeFacts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getStatus, "active"));
        if (activeFacts.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (WorldFact fact : activeFacts) {
            created += certExpiryRule(fact);
            created += stalenessRule(fact);
        }
        if (created > 0) {
            log.info("规则引擎本轮产生 {} 条风险", created);
        }
        return created;
    }

    /** 规则 1：证书 30 天内到期 */
    private int certExpiryRule(WorldFact fact) {
        if (fact.getValidUntil() == null
                || fact.getPredicate() == null
                || !fact.getPredicate().toLowerCase().contains("certificate")) {
            return 0;
        }
        if (Duration.between(Instant.now(), fact.getValidUntil()).compareTo(CERT_EXPIRY_WINDOW) > 0) {
            return 0;
        }
        return createIfAbsent(fact, "cert_expiry", "high", "CERT_EXPIRY_30D",
                "证书「" + fact.getObjectValue() + "」30 天内到期",
                "证书事实（" + fact.getPredicate() + "）将于 " + fact.getValidUntil() + " 到期，建议尽快办理续期。");
    }

    /** 规则 2：事实长期未更新（数据陈旧） */
    private int stalenessRule(WorldFact fact) {
        if (Duration.between(fact.getCreatedAt(), Instant.now()).compareTo(STALENESS_THRESHOLD) <= 0) {
            return 0;
        }
        return createIfAbsent(fact, "data_staleness", "low", "DATA_STALENESS_180D",
                "事实「" + fact.getPredicate() + "」已超过 180 天未更新",
                "该事实创建于 " + fact.getCreatedAt() + "，可能已与现状不符，请核对或更新。");
    }

    private int createIfAbsent(WorldFact fact, String riskType, String level, String ruleCode,
                               String title, String description) {
        Long exists = riskMapper.selectCount(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, fact.getUserId())
                .eq(WorldRisk::getEntityId, fact.getSubjectId())
                .eq(WorldRisk::getRuleCode, ruleCode)
                .ne(WorldRisk::getStatus, "resolved"));
        if (exists != null && exists > 0) {
            return 0;
        }
        Instant now = Instant.now();
        WorldRisk risk = new WorldRisk();
        risk.setUserId(fact.getUserId());
        risk.setRiskType(riskType);
        risk.setLevel(level);
        risk.setTitle(title);
        risk.setDescription(description);
        risk.setEntityId(fact.getSubjectId());
        risk.setRuleCode(ruleCode);
        risk.setStatus("open");
        risk.setCreatedAt(now);
        risk.setUpdatedAt(now);
        riskMapper.insert(risk);
        // P1-6 拍板（联调 2026-11-22）：风险-证据事实关联落库，支撑"某事实影响了哪些风险"反查；
        // 事实链式失效后关联保留，保证风险结论可追溯
        riskFactMapper.insert(new WorldRiskFact(risk.getId(), fact.getId()));
        return 1;
    }
}