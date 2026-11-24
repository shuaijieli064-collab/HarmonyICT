package com.nexus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 HTTP 层验证：新建 run → 异步编排落库 → 执行日志查询接口 → 跨用户隔离。
 * 这一层专门覆盖新增 bean 的 Spring 装配与新接口，弥补纯单元测试的盲区。
 */
@SpringBootTest(classes = com.nexus.api.NexusApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AgentRunHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void logsEndpointReturnsAuditedActionsAndRejectsForeignUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode owner = register("agent_logs_" + suffix);
        JsonNode outsider = register("agent_out_" + suffix);
        String ownerToken = owner.path("data").path("token").asText();
        String outsiderToken = outsider.path("data").path("token").asText();

        JsonNode created = json(post("/api/v1/agent/runs")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"intent\":\"查看有哪些风险并解释\",\"triggerType\":\"manual\"}"));
        assertEquals(0, created.path("code").asInt());
        long runId = created.path("data").path("id").asLong();
        assertTrue(runId > 0);

        JsonNode detail = awaitLogs(ownerToken, runId);
        assertTrue(detail.path("data").path("logs").size() >= 2,
                "L0 只读工具与新增强工具应落库，实际 " + detail);

        JsonNode logs = json(get("/api/v1/agent/runs/" + runId + "/logs?page=1&pageSize=20")
                .header("Authorization", "Bearer " + ownerToken));
        assertEquals(0, logs.path("code").asInt());
        assertTrue(logs.path("data").path("records").size() >= 2);
        JsonNode first = logs.path("data").path("records").get(0);
        assertTrue(first.has("audit"), "日志响应应带审计字段");
        assertTrue(first.path("audit").has("action"));

        JsonNode foreign = json(get("/api/v1/agent/runs/" + runId + "/logs")
                .header("Authorization", "Bearer " + outsiderToken));
        assertEquals(60101, foreign.path("code").asInt(), "跨用户查询必须被拒绝");
    }

    private JsonNode awaitLogs(String token, long runId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            JsonNode detail = json(get("/api/v1/agent/runs/" + runId)
                    .header("Authorization", "Bearer " + token));
            if (detail.path("data").path("logs").size() >= 2) {
                return detail;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("异步编排在 5 秒内未落库日志: runId=" + runId);
    }

    private JsonNode register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"Test@123456\",\"nickname\":\"Agent 测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
