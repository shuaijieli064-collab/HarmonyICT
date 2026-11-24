package com.nexus.insight.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.nexus.insight.dto.SimulationImpact;
import com.nexus.insight.dto.SimulationMonth;
import com.nexus.insight.dto.SimulationRequest;
import com.nexus.insight.dto.SimulationResponse;
import com.nexus.insight.service.DecisionSimulationService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 假设推演实现（P1）：基于订阅类实体事实做 12 个月沙盘投影。
 * 假设性风险使用 SIM_KEEP_* 前缀（不落库），阈值与规则引擎同源（30 天分级）。
 */
@Service
public class DecisionSimulationServiceImpl implements DecisionSimulationService {

    private static final int MONTHS = 12;
    private static final DateTimeFormatter MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WorldEntityMapper entityMapper;
    private final WorldFactMapper factMapper;
    private final WorldRiskMapper riskMapper;

    public DecisionSimulationServiceImpl(WorldEntityMapper entityMapper, WorldFactMapper factMapper,
                                         WorldRiskMapper riskMapper) {
        this.entityMapper = entityMapper;
        this.factMapper = factMapper;
        this.riskMapper = riskMapper;
    }

    @Override
    public SimulationResponse simulate(Long userId, SimulationRequest request) {
        String scenario = request.scenario();
        if (!"cancel".equals(scenario) && !"keep".equals(scenario)) {
            throw new BizException(ResultCode.PARAM_INVALID, "scenario 仅支持 cancel 或 keep");
        }

        WorldEntity entity = entityMapper.selectById(request.entityId());
        if (entity == null || !userId.equals(entity.getUserId())
                || (entity.getStatus() != null && entity.getStatus() != 1)) {
            throw new BizException(ResultCode.WORLD_ENTITY_NOT_FOUND);
        }

        Map<String, WorldFact> facts = loadActiveFactsBySubject(userId, entity.getId());
        WorldFact priceFact = facts.get("subscription_price");
        if (priceFact == null || priceFact.getNormalizedValue() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "该实体缺少 subscription_price 事实，无法推演");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceFact.getNormalizedValue());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "subscription_price 事实不是有效数值");
        }

        String cycle = factValue(facts, "subscription_cycle", "monthly");
        boolean autoRenew = !"false".equalsIgnoreCase(factValue(facts, "auto_renew", "true"));
        BigDecimal budgetLimit = parseOrNull(factValue(facts, "budget_limit", null));

        List<SimulationMonth> months = new ArrayList<>();
        List<SimulationImpact> resolvedRisks = new ArrayList<>();
        List<SimulationImpact> introducedRisks = new ArrayList<>();
        List<String> rationale = new ArrayList<>();
        rationale.add("价格取自事实 subscription_price = " + price.toPlainString()
                + (priceFact.getUnit() == null ? "" : " " + priceFact.getUnit()));
        rationale.add("订阅周期取自事实 subscription_cycle = " + cycle + "（缺失时按 monthly 处理）");
        rationale.add("推演为只读沙盘：不落库、不产生真实风险记录");

        if ("cancel".equals(scenario)) {
            BigDecimal cumulative = BigDecimal.ZERO;
            for (int m = 1; m <= MONTHS; m++) {
                cumulative = cumulative.add(price);
                months.add(new SimulationMonth(m, monthLabel(m), BigDecimal.ZERO, cumulative));
            }
            List<WorldRisk> openRisks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                    .eq(WorldRisk::getUserId, userId)
                    .eq(WorldRisk::getEntityId, entity.getId())
                    .ne(WorldRisk::getStatus, "resolved"));
            for (WorldRisk risk : openRisks) {
                resolvedRisks.add(new SimulationImpact(risk.getRiskType(), risk.getLevel(),
                        risk.getTitle(), risk.getRuleCode()));
            }
            rationale.add("退订口径：该实体名下 " + resolvedRisks.size() + " 条未决风险视为随之消除");
            if (!autoRenew) {
                rationale.add("该订阅未开启自动续费（auto_renew=false），到期不续订即可生效");
            }
            String recommendation = "建议退订：12 个月可节省 " + cumulative.toPlainString()
                    + (budgetLimit != null ? "，月度预算占用同步下降" : "");
            return new SimulationResponse(entity.getId(), entity.getName(), scenario, price, months,
                    resolvedRisks, introducedRisks, recommendation, rationale);
        }

        // keep 场景
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int m = 1; m <= MONTHS; m++) {
            cumulative = cumulative.add(price);
            months.add(new SimulationMonth(m, monthLabel(m), price, cumulative));
        }
        WorldFact renewalFact = facts.get("subscription_renewal_at");
        if (autoRenew && renewalFact != null) {
            Instant renewalAt = parseInstant(renewalFact.getNormalizedValue());
            long daysToRenewal = renewalAt == null ? -1
                    : java.time.Duration.between(Instant.now(), renewalAt).toDays();
            if (daysToRenewal >= 0 && daysToRenewal <= 30) {
                introducedRisks.add(new SimulationImpact("contract", daysToRenewal <= 7 ? "high" : "medium",
                        "保留推演：续费日临近（" + daysToRenewal + " 天后）", "SIM_KEEP_RENEWAL_30D"));
            }
        }
        if (budgetLimit != null && price.compareTo(budgetLimit) > 0) {
            introducedRisks.add(new SimulationImpact("budget", "high",
                    "保留推演：月费用超出预算上限 " + budgetLimit.toPlainString(), "SIM_KEEP_BUDGET_OVER"));
        }
        String recommendation = introducedRisks.isEmpty()
                ? "可保留：12 个月支出 " + cumulative.toPlainString() + "，未触发预算或续费预警"
                : "谨慎保留：存在 " + introducedRisks.size() + " 条假设性风险，建议退订或调整预算";
        rationale.add("保留口径：按 " + cycle + " 周期持续扣费；假设性风险阈值与规则引擎同源（30 天分级）");
        return new SimulationResponse(entity.getId(), entity.getName(), scenario, price, months,
                resolvedRisks, introducedRisks, recommendation, rationale);
    }

    private Map<String, WorldFact> loadActiveFactsBySubject(Long userId, Long subjectId) {
        List<WorldFact> list = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getSubjectId, subjectId)
                .eq(WorldFact::getStatus, "active"));
        Map<String, WorldFact> byPredicate = new HashMap<>();
        for (WorldFact fact : list) {
            byPredicate.putIfAbsent(fact.getPredicate(), fact);
        }
        return byPredicate;
    }

    private static String factValue(Map<String, WorldFact> facts, String predicate, String fallback) {
        WorldFact fact = facts.get(predicate);
        if (fact == null || fact.getNormalizedValue() == null || fact.getNormalizedValue().isBlank()) {
            return fallback;
        }
        return fact.getNormalizedValue();
    }

    private static BigDecimal parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private static String monthLabel(int month) {
        return MONTH_LABEL_FMT.format(LocalDate.now().plusMonths(month));
    }
}
