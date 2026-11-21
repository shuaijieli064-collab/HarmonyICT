package com.nexus.ai.risk;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nexus.ai.RiskAnalysisProvider;
import com.nexus.ai.dto.RiskAnalysisRequest;

/**
 * 风险规则引擎入口：按注册顺序执行全部规则，结果按 ruleCode + 实体 + 标题去重，
 * 再按等级（high → low）排序。只做纯计算，不访问数据库，入库由 A 的服务负责。
 */
@Component
public class DefaultRiskAnalysisProvider implements RiskAnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultRiskAnalysisProvider.class);

    private final List<RiskRule> rules;

    public DefaultRiskAnalysisProvider(List<RiskRule> rules) {
        this.rules = rules == null ? List.of() : rules;
    }

    @Override
    public String providerName() {
        return "rule-engine-v1";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public RiskAnalysisRequest.RiskAnalysisResult analyze(RiskAnalysisRequest request) {
        if (request == null) {
            return new RiskAnalysisRequest.RiskAnalysisResult(List.of());
        }
        RiskEvaluationContext context = new RiskEvaluationContext(
                request.facts(), request.events(), request.analysisAt());

        Map<String, RiskAnalysisRequest.DetectedRisk> unique = new LinkedHashMap<>();
        for (RiskRule rule : rules) {
            for (RiskAnalysisRequest.DetectedRisk risk : rule.evaluate(context)) {
                unique.putIfAbsent(key(risk), risk);
            }
        }

        List<RiskAnalysisRequest.DetectedRisk> sorted = unique.values().stream()
                .sorted(Comparator.comparingInt(
                        (RiskAnalysisRequest.DetectedRisk risk) -> levelWeight(risk.level()))
                        .reversed()
                        .thenComparing(RiskAnalysisRequest.DetectedRisk::title))
                .toList();

        log.debug("风险分析完成 facts={} events={} risks={}",
                context.facts().size(), context.events().size(), sorted.size());
        return new RiskAnalysisRequest.RiskAnalysisResult(sorted);
    }

    private String key(RiskAnalysisRequest.DetectedRisk risk) {
        return risk.ruleCode() + "|" + risk.entityId() + "|" + risk.title();
    }

    private int levelWeight(String level) {
        if ("high".equals(level)) {
            return 3;
        }
        if ("medium".equals(level)) {
            return 2;
        }
        return 1;
    }
}