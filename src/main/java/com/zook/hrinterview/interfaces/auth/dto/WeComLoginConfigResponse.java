package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("企业微信扫码登录配置响应")
public class WeComLoginConfigResponse {

    @ApiModelProperty(value = "企业微信是否启用", required = true, example = "true")
    private Boolean enabled;

    @ApiModelProperty(value = "企业ID", required = true)
    private String corpId;

    @ApiModelProperty(value = "应用AgentId", required = true, example = "1000035")
    private String agentId;

    @ApiModelProperty(value = "登录回调地址", required = true)
    private String redirectUri;

    @ApiModelProperty(value = "登录防串改随机值", required = true)
    private String state;

    @ApiModelProperty(value = "企业微信扫码登录地址", required = true)
    private String loginUrl;
}
