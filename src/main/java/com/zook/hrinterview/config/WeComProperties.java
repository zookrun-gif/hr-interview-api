package com.zook.hrinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    private Boolean enabled = false;

    private String corpId;

    private String agentId;

    private String secret;

    private String redirectUri;

    private Boolean autoCreateUser = true;

    private String defaultRoleCode = "HR";
}
