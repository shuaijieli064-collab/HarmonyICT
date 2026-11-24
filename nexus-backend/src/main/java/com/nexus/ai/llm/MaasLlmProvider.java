package com.nexus.ai.llm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.LlmProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.LlmRequest;
import com.nexus.ai.prompt.PromptTemplateRepository;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * 华为云 MaaS 大模型 Provider（OpenAI 兼容的 chat/completions 协议）。
 * 为什么用 RestClient：Spring 6 内置，避免为一次 HTTP 调用引入额外 SDK 依赖；
 * 模型名、温度、超时全部来自配置，便于切换与评测。
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai", name = "mode", havingValue = "real", matchIfMissing = true)
public class MaasLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(MaasLlmProvider.class);
    private static final String SYSTEM_PROMPT = "你是 NEXUS 的结构化信息抽取引擎，严格按要求只输出 JSON。";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final PromptTemplateRepository promptTemplates;

    public MaasLlmProvider(AiProperties properties, ObjectMapper objectMapper,
            PromptTemplateRepository promptTemplates) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.promptTemplates = promptTemplates;
    }

    @Override
    public String providerName() {
        return "huawei-maas";
    }

    @Override
    public boolean isAvailable() {
        AiProperties.Maas maas = properties.getMaas();
        return !isBlank(maas.getBaseUrl()) && !isBlank(maas.getApiKey()) && !isBlank(maas.getModel());
    }

    @Override
    public LlmRequest.LlmResponse complete(LlmRequest request) {
        if (!isAvailable()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "MaaS 未配置完整，请设置 NEXUS_AI_MAAS_API_KEY 与 NEXUS_AI_MAAS_MODEL");
        }
        AiProperties.Maas maas = properties.getMaas();
        String content = promptTemplates.render(request.templateId(), request.variables());
        double temperature = request.temperature() == null ? maas.getTemperature() : request.temperature();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", maas.getModel());
        payload.put("temperature", temperature);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", content)));
        if (maas.isJsonResponseFormat()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        // 为什么路径也走配置：华为云 MaaS 新旧端点 path 不同（/v1 vs /v2），
        // 写死会让新版端点 404（见 cloud-deployment-plan 3.2）
        String completionPath = normalizePath(maas.getCompletionPath());
        try {
            String body = buildClient().post()
                    .uri(completionPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return parseResponse(body);
        } catch (RestClientException e) {
            log.error("MaaS 调用失败 templateId={} model={}", request.templateId(), maas.getModel(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "MaaS 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("MaaS 响应解析失败 templateId={}", request.templateId(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "MaaS 响应解析失败: " + e.getMessage());
        }
    }

    private RestClient buildClient() {
        AiProperties.Maas maas = properties.getMaas();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, maas.getTimeoutSeconds())).toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        // baseUrl 去掉结尾斜杠，避免与 completionPath 的前导斜杠拼成双斜杠
        String baseUrl = maas.getBaseUrl().stripTrailing();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + maas.getApiKey())
                .build();
    }

    /** 兼容配置漏写前导斜杠（填 v2/chat/completions）与空串（空串回退默认 v1） */
    private String normalizePath(String path) {
        if (isBlank(path)) {
            return "/v1/chat/completions";
        }
        String value = path.strip();
        return value.startsWith("/") ? value : "/" + value;
    }

    private LlmRequest.LlmResponse parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("模型响应缺少 choices");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型响应缺少 message.content");
        }
        String model = root.path("model").asText(properties.getMaas().getModel());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", root.path("id").asText(""));
        raw.put("usage", objectMapper.convertValue(root.path("usage"), Map.class));
        return new LlmRequest.LlmResponse(content, model, raw);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}