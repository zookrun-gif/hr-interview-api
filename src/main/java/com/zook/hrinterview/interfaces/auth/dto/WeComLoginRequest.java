package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("企业微信扫码登录请求")
public class WeComLoginRequest {

    @ApiModelProperty(value = "企业微信回调code", required = true)
    @NotBlank(message = "企业微信登录code不能为空")
    private String code;

    @ApiModelProperty(value = "登录防串改随机值", required = true)
    @NotBlank(message = "企业微信登录state不能为空")
    private String state;
}
