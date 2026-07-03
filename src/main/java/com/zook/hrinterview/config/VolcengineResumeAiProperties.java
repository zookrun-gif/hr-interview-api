package com.zook.hrinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "volcengine.resume-ai")
public class VolcengineResumeAiProperties {

    private Boolean enabled = true;

    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    private String apiKey;

    private String model;

    private Integer timeoutSeconds = 30;

    private Integer maxResumeChars = 12000;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxResumeChars() {
        return maxResumeChars;
    }

    public void setMaxResumeChars(Integer maxResumeChars) {
        this.maxResumeChars = maxResumeChars;
    }
}
