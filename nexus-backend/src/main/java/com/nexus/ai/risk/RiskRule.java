package com.nexus.ai.risk;

import java.util.List;

import com.nexus.ai.dto.RiskAnalysisRequest;

/**
 * 单条风险规则。每条规则只负责一类判断，输出必须带 ruleCode 与证据事实，
 * 保证 world_risks.rule_code 可追溯，且模型不参与结论（规范 6 红线）。
 */
public interface RiskRule {

    List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context);
}