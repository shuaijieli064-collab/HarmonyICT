package com.nexus.ai.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.dto.ExtractionRequest;
import com.nexus.ai.extraction.ExtractionOutputParser;

class RuleBasedExtractorTest {

    private static final String SOURCE_ID = "1948123456789012345";

    private final RuleBasedExtractor extractor = new RuleBasedExtractor(new ObjectMapper());
    private final ExtractionOutputParser parser = new ExtractionOutputParser(new ObjectMapper());

    @Test
    void extractsSubscriptionRenewalAndCycle() {
        String text = """
                您已开通华为云音乐会员
                生效日期：2026-11-18
                下次续费日期：2026-12-18
                订阅周期：按月订阅
                自动续费：已开启自动续费
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(extractor.extract(text, SOURCE_ID), 0.50);

        assertTrue(findFact(parsed, "subscription_renewal_at").isPresent());
        assertEquals(SOURCE_ID,
                findFact(parsed, "subscription_renewal_at").orElseThrow().extra().get("source_id"));
        assertEquals("2026-12-18",
                findFact(parsed, "subscription_renewal_at").map(ExtractionRequest.ExtractedFact::objectValue)
                        .orElse(""));
        assertEquals("monthly",
                findFact(parsed, "subscription_cycle").map(ExtractionRequest.ExtractedFact::objectValue).orElse(""));
        assertEquals("true",
                findFact(parsed, "auto_renew").map(ExtractionRequest.ExtractedFact::objectValue).orElse(""));
    }

    @Test
    void extractsInvoicePriceAndWarrantyEndDate() {
        String text = """
                商品名称：联想拯救者Y9000P 2026款
                开票日期：2026-11-15
                销售方：京东商城
                价税合计：¥8,999.00
                保修政策：整机保修两年
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(extractor.extract(text), 0.50);

        assertEquals("8999.00",
                findFact(parsed, "price").map(ExtractionRequest.ExtractedFact::objectValue).orElse(""));
        ExtractionRequest.ExtractedFact warranty = findFact(parsed, "warranty_period").orElseThrow();
        assertEquals("24", warranty.objectValue());
        assertEquals("month", warranty.extra().get("unit"));
        assertEquals("2028-11-15", warranty.extra().get("valid_until"));
        assertEquals("inferred", warranty.extra().get("fact_status"));
    }

    @Test
    void extractsStudyPlanGoalAndTask() {
        String text = """
                学习计划
                目标：通过华为ICT大赛初赛
                初赛截止：2026年12月20日
                11月30日前完成项目原型
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(extractor.extract(text), 0.50);

        assertTrue(findFact(parsed, "goal_target").isPresent());
        assertTrue(findFact(parsed, "task_due_at").isPresent());
    }

    private Optional<ExtractionRequest.ExtractedFact> findFact(
            ExtractionOutputParser.ParsedExtraction parsed, String predicate) {
        return parsed.facts().stream().filter(f -> predicate.equals(f.predicate())).findFirst();
    }
}