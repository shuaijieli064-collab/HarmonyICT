package com.nexus.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 能力配置。为什么不用 @Value 散落读取：模型、温度、超时、密钥来源需集中管理，
 * 且规范 6 要求关键参数走配置；环境变量通过 Spring relaxed binding 覆盖（如 NEXUS_AI_MAAS_API_KEY）。
 */
@Component
@ConfigurationProperties(prefix = "nexus.ai")
public class AiProperties {

    /** real=调用华为云；mock=本地确定性规则实现，仅用于无凭证环境跑通链路 */
    private Mode mode = Mode.REAL;

    private Maas maas = new Maas();

    private Ocr ocr = new Ocr();

    private Extraction extraction = new Extraction();

    public enum Mode {
        REAL, MOCK
    }

    public static class Maas {
        /** 只到域名，代码追加 completionPath（默认 /v1/chat/completions） */
        private String baseUrl = "https://maas-api.cn-southwest-2.myhuaweicloud.com";
        /**
         * chat/completions 路径：旧端点为 /v1/chat/completions；
         * 新版 ModelArts Studio 全局端点（api.modelarts-maas.com）为 /v2/chat/completions，
         * 以控制台「API调用」面板给出的实际 URL 为准（NEXUS_AI_MAAS_COMPLETION_PATH 覆盖）。
         */
        private String completionPath = "/v1/chat/completions";
        private String apiKey = "";
        private String model = "";
        private double temperature = 0.1;
        private int timeoutSeconds = 60;
        /** 是否要求模型返回 JSON（OpenAI 兼容接口的 response_format） */
        private boolean jsonResponseFormat = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCompletionPath() {
            return completionPath;
        }

        public void setCompletionPath(String completionPath) {
            this.completionPath = completionPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isJsonResponseFormat() {
            return jsonResponseFormat;
        }

        public void setJsonResponseFormat(boolean jsonResponseFormat) {
            this.jsonResponseFormat = jsonResponseFormat;
        }
    }

    public static class Ocr {
        /** OCR 服务域名，代码追加 /v2/{projectId}/ocr/general-text */
        private String endpoint = "https://ocr.cn-north-4.myhuaweicloud.com";
        /** IAM 域名，用于换取 X-Auth-Token */
        private String iamEndpoint = "https://iam.cn-north-4.myhuaweicloud.com";
        private String username = "";
        private String password = "";
        private String domainName = "";
        private String projectId = "";
        private int timeoutSeconds = 60;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getIamEndpoint() {
            return iamEndpoint;
        }

        public void setIamEndpoint(String iamEndpoint) {
            this.iamEndpoint = iamEndpoint;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDomainName() {
            return domainName;
        }

        public void setDomainName(String domainName) {
            this.domainName = domainName;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Extraction {
        /** 低于该置信度的事实不返回给业务层（契约 6.1） */
        private double minConfidence = 0.50;
        /** Schema 校验失败后的重试次数（契约第 9 节） */
        private int maxRetries = 1;

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Maas getMaas() {
        return maas;
    }

    public void setMaas(Maas maas) {
        this.maas = maas;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public void setOcr(Ocr ocr) {
        this.ocr = ocr;
    }

    public Extraction getExtraction() {
        return extraction;
    }

    public void setExtraction(Extraction extraction) {
        this.extraction = extraction;
    }
}