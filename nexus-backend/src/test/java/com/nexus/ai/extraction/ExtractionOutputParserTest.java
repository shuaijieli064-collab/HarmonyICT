package com.nexus.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.dto.ExtractionRequest;

class ExtractionOutputParserTest {

    private final ExtractionOutputParser parser = new ExtractionOutputParser(new ObjectMapper());

    @Test
    void mapsFactAndEntityTypeToWorldModelEnum() {
        String json = """
                {
                  "entities": [
                    {
                      "entityId": "ent_1",
                      "entityType": "device",
                      "name": "联想拯救者 Y9000P",
                      "confidence": 0.96,
                      "evidence": { "textSnippet": "商品名称：联想拯救者Y9000P", "page": 1 }
                    }
                  ],
                  "facts": [
                    {
                      "factId": "fact_1",
                      "subjectRef": "ent_1",
                      "predicate": "purchased_at",
                      "objectValue": "2026-11-15",
                      "valueType": "date",
                      "confidence": 0.95,
                      "factStatus": "confirmed",
                      "validFrom": "2026-11-15",
                      "validUntil": null,
                      "evidence": { "textSnippet": "开票日期：2026-11-15", "page": 1 }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.facts().size());
        ExtractionRequest.ExtractedFact fact = parsed.facts().get(0);
        assertEquals("asset", fact.subjectType());
        assertEquals("联想拯救者 Y9000P", fact.subjectName());
        assertEquals("purchased_at", fact.predicate());
        assertEquals("2026-11-15", fact.objectValue());
        assertEquals(0.95, fact.confidence(), 0.001);
        assertEquals("date", fact.extra().get("value_type"));
        assertEquals("2026-11-15", fact.extra().get("valid_from"));
    }

    @Test
    void dropsFactWithoutEvidenceBecauseConfidenceCappedBelowThreshold() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1",
                      "subjectRef": "ent_1",
                      "predicate": "price",
                      "objectValue": 1000,
                      "valueType": "number",
                      "confidence": 0.90,
                      "factStatus": "confirmed",
                      "evidence": { "textSnippet": "" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertTrue(parsed.facts().isEmpty());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("E-NO-EVIDENCE")));
    }

    @Test
    void rejectsPredicateOutsideControlledVocabulary() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1",
                      "subjectRef": "ent_1",
                      "predicate": "free_text_predicate",
                      "objectValue": "x",
                      "valueType": "string",
                      "confidence": 0.90,
                      "factStatus": "confirmed",
                      "evidence": { "textSnippet": "原文" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertTrue(parsed.facts().isEmpty());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("E-PREDICATE")));
    }

    @Test
    void appliesInferredPenalty() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "device", "name": "笔记本" } ],
                  "facts": [
                    {
                      "factId": "fact_1",
                      "subjectRef": "ent_1",
                      "predicate": "warranty_period",
                      "objectValue": 24,
                      "valueType": "duration",
                      "unit": "month",
                      "confidence": 0.90,
                      "factStatus": "inferred",
                      "evidence": { "textSnippet": "保修两年", "page": 1 }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.facts().size());
        assertEquals(0.77, parsed.facts().get(0).confidence(), 0.001);
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("W-DATE-INFERRED")));
    }

    @Test
    void detectsConflictingValuesAndLowersBoth() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1", "subjectRef": "ent_1", "predicate": "name",
                      "objectValue": "沪深300ETF", "valueType": "string", "confidence": 0.90,
                      "factStatus": "confirmed", "evidence": { "textSnippet": "沪深300ETF" }
                    },
                    {
                      "factId": "fact_2", "subjectRef": "ent_1", "predicate": "name",
                      "objectValue": "中证500ETF", "valueType": "string", "confidence": 0.90,
                      "factStatus": "confirmed", "evidence": { "textSnippet": "中证500ETF" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(2, parsed.facts().size());
        assertEquals(List.of(0.80, 0.80),
                parsed.facts().stream().map(ExtractionRequest.ExtractedFact::confidence).toList());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("W-CONFLICT-VALUE")));
    }

    @Test
    void stripsMarkdownCodeFence() {
        String json = """
                ```json
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "event", "name": "目标" } ],
                  "facts": [
                    {
                      "factId": "fact_1", "subjectRef": "ent_1", "predicate": "goal_target",
                      "objectValue": "通过初赛", "valueType": "string", "confidence": 0.90,
                      "factStatus": "confirmed", "evidence": { "textSnippet": "目标：通过初赛" }
                    }
                  ]
                }
                ```
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.facts().size());
    }

    @Test
    void downgradesIllegalFactStatusToUnverifiedWithWarning() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1", "subjectRef": "ent_1", "predicate": "price",
                      "objectValue": "1000", "valueType": "number", "confidence": 0.90,
                      "factStatus": "verified", "evidence": { "textSnippet": "价格1000元" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.facts().size());
        assertEquals("unverified", parsed.facts().get(0).extra().get("fact_status"));
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("W-INVALID-FACT-STATUS")));
    }

    @Test
    void downgradesLegacyExtractedStatusToUnverified() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1", "subjectRef": "ent_1", "predicate": "name",
                      "objectValue": "沪深300ETF", "valueType": "string", "confidence": 0.90,
                      "factStatus": "extracted", "evidence": { "textSnippet": "沪深300ETF" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals("unverified", parsed.facts().get(0).extra().get("fact_status"));
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("W-INVALID-FACT-STATUS")));
    }

    @Test
    void normalizesFactStatusCaseToLowerCase() {
        String json = """
                {
                  "entities": [ { "entityId": "ent_1", "entityType": "asset", "name": "基金" } ],
                  "facts": [
                    {
                      "factId": "fact_1", "subjectRef": "ent_1", "predicate": "name",
                      "objectValue": "沪深300ETF", "valueType": "string", "confidence": 0.90,
                      "factStatus": "CONFIRMED", "evidence": { "textSnippet": "沪深300ETF" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals("confirmed", parsed.facts().get(0).extra().get("fact_status"));
        assertTrue(parsed.warnings().stream().noneMatch(w -> w.startsWith("W-INVALID-FACT-STATUS")));
    }

    @Test
    void parsesEventsWithSnakeCaseAndNormalizesTimeToUtc() {
        String json = """
                {
                  "extraction_id": "0b6f3c1e-2d4a-4e8b-9c7d-1a2b3c4d5e6f",
                  "source": { "source_id": "194800000000000001" },
                  "language": "zh-CN",
                  "extracted_at": "2026-11-23T00:00:00Z",
                  "entities": [],
                  "facts": [],
                  "events": [
                    {
                      "event_id": "event_1",
                      "event_type": "purchase",
                      "title": "购买笔记本",
                      "start_at": "2026-11-15T00:00:00+08:00",
                      "related_entity_refs": ["ent_1"],
                      "confidence": 0.92,
                      "source_id": "194800000000000001",
                      "evidence": { "text_snippet": "购买日期：2026-11-15", "page": 1 }
                    },
                    {
                      "event_id": "event_2",
                      "event_type": "subscription_renewal",
                      "title": "会员续费",
                      "due_at": "2026-12-15T00:00:00",
                      "confidence": 0.90,
                      "source_id": "194800000000000001",
                      "evidence": { "text_snippet": "下次续费日期：2026-12-15" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(2, parsed.events().size());
        ExtractionRequest.ExtractedEvent purchase = parsed.events().get(0);
        assertEquals("event_1", purchase.eventId());
        assertEquals("asset", purchase.eventType());
        assertEquals("2026-11-14T16:00:00Z", purchase.startAt());
        assertEquals(List.of("ent_1"), purchase.relatedEntityRefs());
        assertEquals("购买日期：2026-11-15", purchase.evidence().get("text_snippet"));

        ExtractionRequest.ExtractedEvent renewal = parsed.events().get(1);
        assertEquals("contract", renewal.eventType());
        assertEquals("2026-12-14T16:00:00Z", renewal.dueAt());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("W-TZ-ASSUMED")));

        assertEquals("0b6f3c1e-2d4a-4e8b-9c7d-1a2b3c4d5e6f", parsed.extractionId());
        assertEquals("zh-CN", parsed.language());
        assertEquals("2026-11-23T00:00:00Z", parsed.extractedAt());
        assertEquals("194800000000000001", parsed.source().get("source_id"));
    }

    @Test
    void dropsEventsWithoutEvidenceOrBelowConfidenceFloor() {
        String json = """
                {
                  "entities": [],
                  "facts": [],
                  "events": [
                    {
                      "event_id": "event_1", "event_type": "purchase", "title": "无证据事件",
                      "start_at": "2026-11-15T00:00:00Z", "confidence": 0.90,
                      "evidence": { "text_snippet": "" }
                    },
                    {
                      "event_id": "event_2", "event_type": "purchase", "title": "低置信事件",
                      "start_at": "2026-11-15T00:00:00Z", "confidence": 0.30,
                      "evidence": { "text_snippet": "原文" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertTrue(parsed.events().isEmpty());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("E-NO-EVIDENCE")));
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.startsWith("E-LOW-CONF")));
    }

    @Test
    void acceptsCamelCaseEventsAsTemporaryCompatibilityFallback() {
        String json = """
                {
                  "entities": [],
                  "facts": [],
                  "events": [
                    {
                      "eventId": "event_1", "eventType": "subscription_start", "title": "开通会员",
                      "startAt": "2026-11-15T00:00:00+08:00", "confidence": 0.90,
                      "evidence": { "textSnippet": "您已开通会员" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.events().size());
        assertEquals("contract", parsed.events().get(0).eventType());
        assertEquals("2026-11-14T16:00:00Z", parsed.events().get(0).startAt());
    }

    @Test
    void mapsPaymentEventToContractPerBContract() {
        String json = """
                {
                  "entities": [],
                  "facts": [],
                  "events": [
                    {
                      "event_id": "event_1", "event_type": "payment", "title": "支付订阅费",
                      "start_at": "2026-11-15T10:00:00+08:00", "confidence": 0.90,
                      "evidence": { "text_snippet": "支付订阅费 39 元" }
                    }
                  ]
                }
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.events().size());
        assertEquals("contract", parsed.events().get(0).eventType());
    }
}