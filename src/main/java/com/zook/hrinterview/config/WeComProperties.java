package com.zook.hrinterview.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    @ApiModelProperty(value = "是否启用企业微信登录；false 时不走企微扫码登录", example = "true")
    private Boolean enabled = false;

    @ApiModelProperty(value = "企业微信 CorpId；来自企业微信后台")
    private String corpId;

    @ApiModelProperty(value = "企业微信应用 AgentId；用于识别当前 HR 系统应用", example = "1000035")
    private String agentId;

    @ApiModelProperty(value = "企业微信应用 Secret；用于换取 access_token，属于敏感配置")
    private String secret;

    @ApiModelProperty(value = "企业微信 OAuth 回调地址；必须和企微后台配置的可信回调域名一致", example = "https://zook.kaixinzou.cn/login/wecom-callback")
    private String redirectUri;

    @ApiModelProperty(value = "企微登录成功但系统用户不存在时，是否自动创建用户", example = "true")
    private Boolean autoCreateUser = true;

    @ApiModelProperty(value = "企微自动创建用户时绑定的默认角色编码", example = "HR")
    private String defaultRoleCode = "HR";
}
