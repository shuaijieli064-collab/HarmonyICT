package com.nexus.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.LlmRequest;
import com.nexus.ai.prompt.PromptTemplateRepository;

/**
 * MaasLlmProvider 纯逻辑单测：normalizePath / isAvailable / parseResponse。
 * 用反射直接调私有方法，不触发 complete()，因此不会发起任何真实网络请求。
 */
class MaasLlmProviderTest {

    private AiProperties properties;
    private MaasLlmProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        provider = new MaasLlmProvider(properties, new ObjectMapper(), mock(PromptTemplateRepository.class));
    }

    // ---------- normalizePath ----------

    @Test
    void normalizePath_v1Path_keepsSlash() throws Exception {
        assertEquals("/v1/chat/completions", normalizePath("/v1/chat/completions"));
    }

    @Test
    void normalizePath_v2Path_keepsSlash() throws Exception {
        assertEquals("/v2/chat/completions", normalizePath("/v2/chat/completions"));
    }

    @Test
    void normalizePath_missingLeadingSlash_prependsSlash() throws Exception {
        assertEquals("/v2/chat/completions", normalizePath("v2/chat/completions"));
    }

    @Test
    void normalizePath_blank_fallsBackToV1() throws Exception {
        assertEquals("/v1/chat/completions", normalizePath(""));
        assertEquals("/v1/chat/completions", normalizePath("   "));
        assertEquals("/v1/chat/completions", normalizePath(null));
    }

    @Test
    void normalizePath_surroundingWhitespace_isStripped() throws Exception {
        assertEquals("/v2/chat/completions", normalizePath("  /v2/chat/completions  "));
    }

    // ---------- isAvailable ----------

    @Test
    void isAvailable_missingKeyOrModel_returnsFalse() {
        AiProperties.Maas maas = properties.getMaas();
        // 默认 baseUrl 有值，但 apiKey/model 为空
        assertFalse(provider.isAvailable());

        maas.setApiKey("key");
        assertFalse(provider.isAvailable());

        maas.setApiKey("");
        maas.setModel("model");
        assertFalse(provider.isAvailable());
    }

    @Test
    void isAvailable_missingBaseUrl_returnsFalse() {
        AiProperties.Maas maas = properties.getMaas();
        maas.setApiKey("key");
        maas.setModel("model");
        maas.setBaseUrl("  ");
        assertFalse(provider.isAvailable());
    }

    @Test
    void isAvailable_allConfigured_returnsTrue() {
        AiProperties.Maas maas = properties.getMaas();
        maas.setBaseUrl("https://maas.example.com");
        maas.setApiKey("key");
        maas.setModel("model");
        assertTrue(provider.isAvailable());
    }

    // ---------- parseResponse ----------

    @Test
    void parseResponse_normalChoices_returnsContentAndModel() throws Exception {
        properties.getMaas().setModel("configured-model");
        String body = """
                {
                  "id": "chat-123",
                  "model": "model-from-response",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "抽取结果 JSON"}}
                  ],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20}
                }
                """;

        LlmRequest.LlmResponse response = parseResponse(body);

        assertEquals("抽取结果 JSON", response.content());
        assertEquals("model-from-response", response.model());
        assertEquals("chat-123", response.rawResponse().get("id"));
    }

    @Test
    void parseResponse_blankContent_throws() throws Exception {
        String body = """
                {"choices": [{"message": {"content": "  "}}]}
                """;

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parseResponse(body));
        assertEquals("模型响应缺少 message.content", ex.getMessage());
    }

    @Test
    void parseResponse_missingChoices_throws() {
        String body = "{\"id\": \"chat-123\", \"model\": \"m\"}";

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parseResponse(body));
        assertEquals("模型响应缺少 choices", ex.getMessage());
    }

    @Test
    void parseResponse_emptyChoices_throws() {
        String body = "{\"choices\": []}";

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parseResponse(body));
        assertEquals("模型响应缺少 choices", ex.getMessage());
    }

    // ---------- 反射辅助 ----------

    private String normalizePath(String path) throws Exception {
        Method method = MaasLlmProvider.class.getDeclaredMethod("normalizePath", String.class);
        method.setAccessible(true);
        return (String) method.invoke(provider, path);
    }

    private LlmRequest.LlmResponse parseResponse(String body) throws Exception {
        Method method = MaasLlmProvider.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        try {
            return (LlmRequest.LlmResponse) method.invoke(provider, body);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
