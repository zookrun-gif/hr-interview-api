package com.zook.hrinterview.component.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.zook.hrinterview.utils.HttpRestClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiChatComponent {

    @Resource
    private OpenAiChatProperties properties;

    @Resource
    private HttpRestClient httpRestClient;

    public boolean available() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getModel());
    }

    public String chat(String systemPrompt, String userPrompt, Double temperature) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("temperature", temperature == null ? 0.2 : temperature);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        JsonNode root = httpRestClient.postJsonForJson(
                chatCompletionsUrl(),
                requestBody,
                properties.getApiKey(),
                properties.getTimeoutSeconds()
        );
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    public String model() {
        return properties.getModel();
    }

    private String chatCompletionsUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "chat/completions";
        }
        return baseUrl + "/chat/completions";
    }
}
