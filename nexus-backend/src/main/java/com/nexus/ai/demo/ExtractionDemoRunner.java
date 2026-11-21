package com.nexus.ai.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.LlmProvider;
import com.nexus.ai.OcrProvider;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.dto.LlmRequest;
import com.nexus.ai.dto.OcrRequest;
import com.nexus.ai.extraction.ExtractionOutputParser;

/**
 * 信息抽取 Demo：读取测试资料 → OCR（图片）或直接读文本 → 大模型抽取 → 校验 → 落盘 JSON。
 * 默认关闭，设置 nexus.ai.demo.enabled=true 才运行，避免正常启动触发计费调用。
 * 运行时从 nexus-backend 目录执行，默认读取 ../nexus-ai/testdata。
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.demo", name = "enabled", havingValue = "true")
public class ExtractionDemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionDemoRunner.class);
    private static final String TEMPLATE_ID = "extraction";

    private final OcrProvider ocrProvider;
    private final LlmProvider llmProvider;
    private final ExtractionOutputParser outputParser;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final String inputFile;
    private final String outputFile;

    public ExtractionDemoRunner(OcrProvider ocrProvider, LlmProvider llmProvider,
            ExtractionOutputParser outputParser, ObjectMapper objectMapper,
            AiProperties properties,
            @Value("${nexus.ai.demo.input:../nexus-ai/testdata/subscription.txt}") String inputFile,
            @Value("${nexus.ai.demo.output:../nexus-ai/testdata/extraction-result.json}") String outputFile) {
        this.ocrProvider = ocrProvider;
        this.llmProvider = llmProvider;
        this.outputParser = outputParser;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path input = Path.of(inputFile);
        if (!Files.exists(input)) {
            log.error("抽取 Demo 输入文件不存在: {}", input.toAbsolutePath());
            return;
        }

        String sourceText = readSourceText(input);
        if (sourceText.isBlank()) {
            log.error("抽取 Demo 未获得有效文本，mock 模式不支持图片识别: {}", input);
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sourceText", sourceText);
        variables.put("schemaHints", "device、subscription、goal、task");

        LlmRequest.LlmResponse response = llmProvider.complete(
                new LlmRequest(TEMPLATE_ID, variables, null));
        ExtractionOutputParser.ParsedExtraction parsed = outputParser.parse(
                response.content(), properties.getExtraction().getMinConfidence());

        JsonNode json = objectMapper.readTree(response.content());
        Path output = Path.of(outputFile);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                StandardCharsets.UTF_8);

        log.info("抽取 Demo 完成 provider={} model={} facts={} overallConfidence={} warnings={} output={}",
                llmProvider.providerName(), response.model(), parsed.facts().size(),
                parsed.overallConfidence(), parsed.warnings(), output.toAbsolutePath());
    }

    private String readSourceText(Path input) throws Exception {
        String fileName = input.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
            return Files.readString(input, StandardCharsets.UTF_8);
        }
        byte[] bytes = Files.readAllBytes(input);
        String mimeType = fileName.endsWith(".png") ? "image/png"
                : fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "image/jpeg"
                        : fileName.endsWith(".pdf") ? "pdf" : "application/octet-stream";
        OcrRequest.OcrResult result = ocrProvider.recognize(
                new OcrRequest(null, Base64.getEncoder().encodeToString(bytes), mimeType));
        return result.fullText();
    }
}