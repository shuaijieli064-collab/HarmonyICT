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
                      "factStatus": "extracted",
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
        assertEquals("date", fact.extra().get("valueType"));
        assertEquals("2026-11-15", fact.extra().get("validFrom"));
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
                      "factStatus": "extracted",
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
                      "factStatus": "extracted",
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
                      "factStatus": "extracted", "evidence": { "textSnippet": "沪深300ETF" }
                    },
                    {
                      "factId": "fact_2", "subjectRef": "ent_1", "predicate": "name",
                      "objectValue": "中证500ETF", "valueType": "string", "confidence": 0.90,
                      "factStatus": "extracted", "evidence": { "textSnippet": "中证500ETF" }
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
                      "factStatus": "extracted", "evidence": { "textSnippet": "目标：通过初赛" }
                    }
                  ]
                }
                ```
                """;

        ExtractionOutputParser.ParsedExtraction parsed = parser.parse(json, 0.50);

        assertEquals(1, parsed.facts().size());
    }
}