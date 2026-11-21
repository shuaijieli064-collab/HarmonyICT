package com.nexus.ai.mock;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nexus.ai.LlmProvider;
import com.nexus.ai.dto.LlmRequest;

/**
 * 本地 mock 大模型实现。仅用于无云凭证时验证「模板渲染 → JSON → Schema 校验」链路；
 * 输出由 RuleBasedExtractor 基于原文确定性生成，不含写死的业务数据。
 * 真实演示与验收必须设置 nexus.ai.mode=real。
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai", name = "mode", havingValue = "mock")
public class MockLlmProvider implements LlmProvider {

    private static final String EXTRACTION_TEMPLATE = "extraction";
    private static final String RISK_EXPLAIN_TEMPLATE = "risk-explain";

    private final RuleBasedExtractor ruleBasedExtractor;

    public MockLlmProvider(RuleBasedExtractor ruleBasedExtractor) {
        this.ruleBasedExtractor = ruleBasedExtractor;
    }

    @Override
    public String providerName() {
        return "mock-rule-based";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LlmRequest.LlmResponse complete(LlmRequest request) {
        if (EXTRACTION_TEMPLATE.equals(request.templateId())) {
            Object sourceText = request.variables() == null ? null : request.variables().get("sourceText");
            String content = ruleBasedExtractor.extract(sourceText == null ? "" : String.valueOf(sourceText));
            return new LlmRequest.LlmResponse(content, "mock-rule-based", Map.of());
        }
        if (RISK_EXPLAIN_TEMPLATE.equals(request.templateId())) {
            String content = "该风险由固定规则命中产生，说明中的日期与金额均来自已入库事实，"
                    + "请按规则提示的时间窗口处理，避免错过关键节点。";
            return new LlmRequest.LlmResponse(content, "mock-rule-based", Map.of());
        }
        return new LlmRequest.LlmResponse("{}", "mock-rule-based", Map.of());
    }
}