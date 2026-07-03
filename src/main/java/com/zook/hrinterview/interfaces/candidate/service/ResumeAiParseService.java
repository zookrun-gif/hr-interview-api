package com.zook.hrinterview.interfaces.candidate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.config.VolcengineResumeAiProperties;
import com.zook.hrinterview.interfaces.candidate.dto.ResumeParseResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeAiParseService {

    @Resource
    private VolcengineResumeAiProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    public boolean available() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getModel());
    }

    public boolean fillProfile(ResumeParseResponse response, String plainText) {
        if (!available() || !StringUtils.hasText(plainText)) {
            return false;
        }
        try {
            JsonNode root = callVolcengine(buildPrompt(limitResumeText(plainText)));
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode profile = objectMapper.readTree(extractJson(content));
            applyProfile(response, profile);
            response.setAiParsed(Boolean.TRUE);
            response.setAiConfidence(profile.path("confidence").isNumber() ? profile.path("confidence").doubleValue() : null);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private JsonNode callVolcengine(String prompt) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("temperature", 0);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", prompt)
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds() == null ? 30 : properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("resume ai parse failed");
        }
        return objectMapper.readTree(response.body());
    }

    private String systemPrompt() {
        return "你是专业 HR 简历解析助手。只输出严格 JSON，不要输出 Markdown。"
                + "无法确认的字段填空字符串或 null。性别只能输出 MALE、FEMALE、UNKNOWN。"
                + "年龄优先用生日计算，无法计算则用简历年龄字段。";
    }

    private String buildPrompt(String plainText) {
        return "请从下面简历文本中抽取结构化信息，严格输出 JSON：\n"
                + "{\n"
                + "  \"name\":\"\",\n"
                + "  \"gender\":\"MALE|FEMALE|UNKNOWN\",\n"
                + "  \"age\":null,\n"
                + "  \"birthday\":\"\",\n"
                + "  \"phone\":\"\",\n"
                + "  \"email\":\"\",\n"
                + "  \"workYears\":null,\n"
                + "  \"highestDegree\":\"\",\n"
                + "  \"school\":\"\",\n"
                + "  \"major\":\"\",\n"
                + "  \"expectedPosition\":\"\",\n"
                + "  \"skills\":[],\n"
                + "  \"workExperiences\":[],\n"
                + "  \"projectExperiences\":[],\n"
                + "  \"selfEvaluation\":\"\",\n"
                + "  \"summary\":\"\",\n"
                + "  \"confidence\":0.0,\n"
                + "  \"warnings\":[]\n"
                + "}\n\n简历文本：\n" + plainText;
    }

    private void applyProfile(ResumeParseResponse response, JsonNode profile) {
        response.setName(text(profile, "name"));
        response.setGender(normalizeGender(text(profile, "gender")));
        if (profile.path("age").isInt() || profile.path("age").isLong()) {
            int age = profile.path("age").asInt();
            response.setAge(age >= 0 && age <= 120 ? age : null);
        }
        response.setPhone(text(profile, "phone"));
        response.setEmail(text(profile, "email"));
        response.setStructuredJson(profile.toString());
    }

    private String normalizeGender(String gender) {
        if ("MALE".equalsIgnoreCase(gender) || "男".equals(gender)) {
            return "MALE";
        }
        if ("FEMALE".equalsIgnoreCase(gender) || "女".equals(gender)) {
            return "FEMALE";
        }
        return StringUtils.hasText(gender) ? "UNKNOWN" : "";
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private String limitResumeText(String plainText) {
        int maxChars = properties.getMaxResumeChars() == null ? 12000 : properties.getMaxResumeChars();
        return plainText.length() <= maxChars ? plainText : plainText.substring(0, maxChars);
    }

    private String extractJson(String content) {
        String value = content == null ? "" : content.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }
}
