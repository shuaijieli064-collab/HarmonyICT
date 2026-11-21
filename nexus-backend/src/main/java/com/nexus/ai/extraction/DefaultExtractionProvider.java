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
 * 为什么只映射 facts：A 的 ExtractionResult 当前只有 facts 字段；事件/任务/证据的完整结构
 * 由抽取 Demo 输出 JSON 保留，入库契约扩展待与 A 走 PR 确认（extraction-schema.md 第 12 节）。
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
                if (!parsed.warnings().isEmpty()) {
                    log.info("抽取完成但存在告警 provider={} warnings={}", response.model(), parsed.warnings());
                }
                return new ExtractionRequest.ExtractionResult(parsed.facts(), parsed.overallConfidence());
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