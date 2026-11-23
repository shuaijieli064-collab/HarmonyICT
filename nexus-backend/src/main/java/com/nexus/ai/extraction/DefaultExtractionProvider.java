package com.nexus.ai.extraction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nexus.ai.ExtractionProvider;
import com.nexus.ai.LlmProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ai.dto.LlmRequest;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * 抽取 Provider 默认实现：Prompt → 大模型 → Schema 校验 → DTO 映射。
 * 2026-11-23 events 接入后映射 facts + events 与四个顶层字段；tasks 暂不落库，仅保留在抽取 Demo JSON 中。
 */
@Component
public class DefaultExtractionProvider implements ExtractionProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultExtractionProvider.class);
    private static final String TEMPLATE_ID = "extraction";

    private final LlmProvider llmProvider;
    private final ExtractionOutputParser outputParser;
    private final AiProperties properties;

    public DefaultExtractionProvider(LlmProvider llmProvider, ExtractionOutputParser outputParser,
            AiProperties properties) {
        this.llmProvider = llmProvider;
        this.outputParser = outputParser;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return llmProvider.providerName() + "+schema-v1";
    }

    @Override
    public boolean isAvailable() {
        return llmProvider.isAvailable();
    }

    @Override
    public ExtractionRequest.ExtractionResult extract(ExtractionRequest request) {
        if (request == null || request.sourceText() == null || request.sourceText().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "抽取原文不能为空");
        }
        // sourceId 为 A 上传接口返回的雪花数字字符串，B 只透传，禁止自造 UUID（PR#1 拍板）
        if (request.sourceId() == null || request.sourceId().isBlank()) {
            throw new BizException(ResultCode.EXTRACTION_NO_SOURCE, "抽取请求缺少 sourceId");
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sourceId", request.sourceId());
        variables.put("sourceText", request.sourceText());
        variables.put("schemaHints", joinHints(request.schemaHints()));

        int maxRetries = Math.max(0, properties.getExtraction().getMaxRetries());
        for (int attempt = 0;; attempt++) {
            LlmRequest.LlmResponse response = llmProvider.complete(
                    new LlmRequest(TEMPLATE_ID, variables, null));
            try {
                ExtractionOutputParser.ParsedExtraction parsed = outputParser.parse(
                        response.content(), properties.getExtraction().getMinConfidence());
                // E-PREDICATE：Parser 层逐条丢弃并记 warning；Provider 对外阻断整份（90013），
                // 不重试，避免模型「凑词」。禁止把未登记谓词静默入库（extraction-schema.md 第 8 节）。
                if (parsed.warnings().stream().anyMatch(w -> w.startsWith("E-PREDICATE"))) {
                    throw new BizException(ResultCode.EXTRACTION_PREDICATE,
                            "抽取结果含未登记谓词: " + parsed.warnings().stream()
                                    .filter(w -> w.startsWith("E-PREDICATE")).toList());
                }
                if (!parsed.warnings().isEmpty()) {
                    log.info("抽取完成但存在告警 provider={} warnings={}", response.model(), parsed.warnings());
                }
                return new ExtractionRequest.ExtractionResult(
                        parsed.facts(), parsed.overallConfidence(), parsed.events(),
                        parsed.extractionId(), parsed.source(), parsed.language(), parsed.extractedAt());
            } catch (ExtractionSchemaException e) {
                if (attempt >= maxRetries) {
                    log.error("抽取结果不符合 Schema，已重试 {} 次", maxRetries, e);
                    throw new BizException(ResultCode.EXTRACTION_SCHEMA, "抽取结果不符合 Schema: " + e.getMessage());
                }
                log.warn("抽取结果不符合 Schema，准备第 {} 次重试: {}", attempt + 1, e.getMessage());
            }
        }
    }

    private String joinHints(List<String> hints) {
        return hints == null || hints.isEmpty() ? "按资料类型自动判断" : String.join("、", hints);
    }
}