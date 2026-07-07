package com.zook.hrinterview.component.ai.openai;

import com.zook.hrinterview.common.constant.ThirdPartyApiConstant;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openai.chat")
public class OpenAiChatProperties {

    @ApiModelProperty(value = "是否启用 OpenAI 兼容接口；当前用于 AI 面试报告生成", example = "true")
    private Boolean enabled = false;

    @ApiModelProperty(value = "OpenAI 兼容接口 Base URL；如果走中转站，填中转站 /v1 地址", example = "http://106.55.174.126:30088/v1")
    private String baseUrl = ThirdPartyApiConstant.OPENAI_CHAT_BASE_URL;

    @ApiModelProperty(value = "OpenAI 兼容接口 API Key；属于敏感配置")
    private String apiKey;

    @ApiModelProperty(value = "OpenAI 兼容接口模型名称；用于面试报告打分和总结", example = "gpt-5.4-mini")
    private String model = "gpt-5.4-mini";

    @ApiModelProperty(value = "OpenAI 兼容接口请求超时时间，单位秒；报告生成慢可适当调大", example = "60")
    private Integer timeoutSeconds = 60;
}
