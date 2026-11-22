package com.nexus.ai.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * 提示词模板仓库。为什么集中成文件：规范 6 禁止提示词散落在代码中，
 * 模板放在 classpath:ai/prompts/ 下，按 templateId 引用，便于评审与迭代。
 */
@Component
public class PromptTemplateRepository {

    private static final String TEMPLATE_ROOT = "ai/prompts/";
    private static final String TEMPLATE_SUFFIX = ".md";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 加载模板并替换 {{key}} 变量。变量值按 String 注入，避免模板引擎引入额外依赖。
     */
    public String render(String templateId, Map<String, Object> variables) {
        String template = cache.computeIfAbsent(templateId, this::load);
        String rendered = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
            }
        }
        return rendered;
    }

    private String load(String templateId) {
        String location = TEMPLATE_ROOT + templateId + TEMPLATE_SUFFIX;
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "提示词模板不存在: " + location);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "提示词模板读取失败: " + location);
        }
    }
}