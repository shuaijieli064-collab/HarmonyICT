package com.nexus.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ingestion.pipeline.ExtractionPipelineService;
import com.nexus.risk.pipeline.RiskAnalysisPipelineService;
import com.nexus.risk.rule.RiskRuleEngine;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import com.nexus.world.mapper.WorldSourceMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成测试（本地 H2 + mock，无云依赖）。
 * 目标：用真实 HTTP 层（MockMvc）走通 注册→登录→上传→抽取落库→事件→风险扫描→查询，
 * 并验证 user_id 数据隔离。这些链路此前只有单元/契约测试覆盖，缺少整体实证。
 *
 * 为什么同步调用 pipeline：上传触发的 @Async 抽取在测试上下文时序不稳定，
 * 上传接口仍走 HTTP 验证，抽取环节改为显式调用同一 service 方法，保证断言确定性；
 * 风险同理直接调用 scan() 而不等 60s 定时器。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

    private static final String SUBSCRIPTION_TEXT = """
            订阅确认单
            您已开通云盘会员（按月自动续费）
            销售方：某某云服务有限公司
            金额：¥30.00
            下次续费日期：2026-12-05
            月度预算：¥50
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExtractionPipelineService extractionPipelineService;
    @Autowired
    private RiskAnalysisPipelineService riskAnalysisPipelineService;
    @Autowired
    private RiskRuleEngine riskRuleEngine;
    @Autowired
    private WorldSourceMapper sourceMapper;
    @Autowired
    private WorldFactMapper factMapper;
    @Autowired
    private WorldEventMapper eventMapper;

    private static String tokenA;
    private static Long userIdA;
    private static Long userIdB;
    private static Long sourceId;
    private static String tokenB;

    @Test
    @Order(1)
    void step1_registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userA = "e2e_a_" + suffix;
        String userB = "e2e_b_" + suffix;

        JsonNode regA = postJson("/api/v1/auth/register",
                "{\"username\":\"" + userA + "\",\"password\":\"Test@123456\",\"nickname\":\"端到端A\"}");
        assertEquals(0, regA.path("code").asInt());
        userIdA = regA.path("data").path("userId").asLong();
        tokenA = regA.path("data").path("token").asText();
        assertNotNull(tokenA);

        JsonNode regB = postJson("/api/v1/auth/register",
                "{\"username\":\"" + userB + "\",\"password\":\"Test@123456\",\"nickname\":\"端到端B\"}");
        assertEquals(0, regB.path("code").asInt());
        userIdB = regB.path("data").path("userId").asLong();
        tokenB = regB.path("data").path("token").asText();

        // 登录路径：同一账号换 token 必须成功
        JsonNode login = postJson("/api/v1/auth/login",
                "{\"username\":\"" + userA + "\",\"password\":\"Test@123456\"}");
        assertEquals(0, login.path("code").asInt());
        assertFalse(login.path("data").path("token").asText().isBlank());
    }

    @Test
    @Order(2)
    void step2_uploadSource() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "subscription.txt", MediaType.TEXT_PLAIN_VALUE,
                SUBSCRIPTION_TEXT.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/sources/upload").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        sourceId = data.path("sourceId").asLong();
        assertTrue(sourceId > 0);

        // 上传接口内部已触发 @Async 抽取；轮询等待状态机跑到终态，证明真实异步链路可用
        String status = pollSourceStatus(userIdA, sourceId, 5000);
        assertEquals("parsed", status, "抽取后状态应为 parsed，实际 " + status);
    }

    @Test
    @Order(3)
    void step3_factsAndEventsPersisted() throws Exception {
        // 事实：订阅续费日、周期、自动续费、价格、budget_limit 至少 4 条
        var facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userIdA)
                .eq(WorldFact::getSourceId, sourceId));
        assertTrue(facts.size() >= 4, "订阅样例应落库至少 4 条事实，实际 " + facts.size());
        assertTrue(facts.stream().anyMatch(f -> "budget_limit".equals(f.getPredicate())),
                "B 的 budget_limit 谓词应落库");
        assertTrue(facts.stream().allMatch(f -> f.getConfidence() != null),
                "事实须带置信度");

        // 事件：subscription_renewal 事件，时间归一为 UTC
        var events = eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userIdA)
                .eq(WorldEvent::getSourceId, sourceId));
        assertFalse(events.isEmpty(), "应落库至少 1 条事件");
        // Parser 按契约 3.4 将 subscription_renewal 归一为 6 值之一：contract
        assertTrue(events.stream().anyMatch(e -> "contract".equals(e.getEventType())),
                "应有 contract 事件（subscription_renewal 归一后）");
        assertTrue(events.stream().allMatch(e -> e.getOccurredAt() != null),
                "事件须有 occurred_at（+08:00 已归一 UTC）");

        // 通过 HTTP 查询事件列表（前端时间线消费路径）
        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(4)
    void step4_riskScanAndList() throws Exception {
        // 直接触发两类风险扫描（不依赖 60s 定时器）
        riskRuleEngine.scanNow();
        riskAnalysisPipelineService.scanNow();
        // 风险只能由规则产生，列表接口应可用且不报错
        mockMvc.perform(get("/api/v1/risks").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(5)
    void step5_userIsolation() throws Exception {
        // 用户 B 不能读取用户 A 上传的来源详情（应 404 类业务错误或 20102）
        MvcResult result = mockMvc.perform(get("/api/v1/sources/" + sourceId)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        int code = objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asInt();
        assertTrue(code != 0, "跨用户读取来源应失败，实际 code=" + code);

        // 用户 B 名下没有任何事实（A 的抽取结果未串到 B）
        var bFacts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userIdB));
        assertTrue(bFacts.isEmpty());
    }

    /** 轮询来源状态至终态（上传触发的 @Async 抽取需等待），返回最后观察到的状态。 */
    private String pollSourceStatus(Long userId, Long id, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            WorldSource source = sourceMapper.selectById(id);
            if (source != null && source.getUserId().equals(userId)) {
                last = source.getStatus();
                if ("parsed".equals(last) || "failed".equals(last)) {
                    return last;
                }
            }
            Thread.sleep(50);
        }
        return last;
    }

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
