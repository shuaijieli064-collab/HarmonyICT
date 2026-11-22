package com.nexus.ai.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.OcrProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.OcrRequest;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 华为云 OCR Provider（通用文字识别）。
 * 认证方式：IAM 用户名/密码换 X-Auth-Token，缓存 20 分钟；不在代码中硬编码任何凭据。
 * 为什么 PDF 暂不支持：华为云通用文字识别入参为单张图片，PDF 需先转图片（阶段二随 OBS 接入补齐）。
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai", name = "mode", havingValue = "real", matchIfMissing = true)
public class HuaweiOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(HuaweiOcrProvider.class);
    private static final String IAM_TOKEN_PATH = "/v3/auth/tokens";
    private static final String OCR_PATH = "/v2/%s/ocr/general-text";
    private static final long TOKEN_TTL_SECONDS = 1200;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpireAt = Instant.EPOCH;

    public HuaweiOcrProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "huawei-ocr";
    }

    @Override
    public boolean isAvailable() {
        AiProperties.Ocr ocr = properties.getOcr();
        return !isBlank(ocr.getEndpoint()) && !isBlank(ocr.getProjectId())
                && !isBlank(ocr.getUsername()) && !isBlank(ocr.getPassword())
                && !isBlank(ocr.getDomainName());
    }

    @Override
    public OcrRequest.OcrResult recognize(OcrRequest request) {
        if (!isAvailable()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "OCR 未配置完整，请设置 NEXUS_AI_OCR_USERNAME/PASSWORD/DOMAIN_NAME/PROJECT_ID");
        }
        if (isBlank(request.contentBase64())) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "阶段一 OCR 仅支持 base64 图片输入，OBS 对象读取待阶段二接入");
        }
        String mimeType = request.mimeType() == null ? "" : request.mimeType().toLowerCase();
        if ("pdf".equals(mimeType)) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "PDF 需先转换为图片再识别，阶段二接入 PDF 转图");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("image", request.contentBase64());

        try {
            String body = buildClient().post()
                    .uri(String.format(OCR_PATH, properties.getOcr().getProjectId()))
                    .header("X-Auth-Token", obtainToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return parseResult(body);
        } catch (RestClientException e) {
            log.error("OCR 调用失败 mimeType={}", mimeType, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "OCR 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 响应解析失败", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "OCR 响应解析失败: " + e.getMessage());
        }
    }

    private OcrRequest.OcrResult parseResult(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode result = root.path("result");
        List<OcrRequest.OcrResult.TextBlock> blocks = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        double totalConfidence = 0;
        int count = 0;

        JsonNode words = result.path("words");
        if (words.isArray()) {
            for (JsonNode word : words) {
                String text = word.path("words").asText("");
                if (text.isBlank()) {
                    continue;
                }
                double confidence = word.path("confidence").asDouble(0);
                blocks.add(new OcrRequest.OcrResult.TextBlock(1, text, confidence));
                fullText.append(text);
                totalConfidence += confidence;
                count++;
            }
        } else {
            String text = result.path("text").asText("");
            if (!text.isBlank()) {
                blocks.add(new OcrRequest.OcrResult.TextBlock(1, text, 1.0));
                fullText.append(text);
                totalConfidence += 1.0;
                count++;
            }
        }
        double overall = count == 0 ? 0 : totalConfidence / count;
        return new OcrRequest.OcrResult(fullText.toString(), blocks, overall);
    }

    private String obtainToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpireAt)) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpireAt)) {
                return cachedToken;
            }
            AiProperties.Ocr ocr = properties.getOcr();
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("name", ocr.getUsername());
            user.put("password", ocr.getPassword());
            user.put("domain", Map.of("name", ocr.getDomainName()));

            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("identity", Map.of(
                    "methods", List.of("password"),
                    "password", Map.of("user", user)));
            auth.put("scope", Map.of("project", Map.of("id", ocr.getProjectId())));

            try {
                String body = buildClient().post()
                        .uri(ocr.getIamEndpoint() + IAM_TOKEN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("auth", auth))
                        .retrieve()
                        .toEntity(String.class)
                        .getHeaders()
                        .getFirst("X-Subject-Token");
                if (isBlank(body)) {
                    throw new IllegalStateException("IAM 响应缺少 X-Subject-Token");
                }
                cachedToken = body;
                tokenExpireAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);
                return cachedToken;
            } catch (RestClientException e) {
                log.error("IAM 获取 token 失败", e);
                throw new BizException(ResultCode.INTERNAL_ERROR, "IAM 认证失败: " + e.getMessage());
            }
        }
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, properties.getOcr().getTimeoutSeconds())).toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}