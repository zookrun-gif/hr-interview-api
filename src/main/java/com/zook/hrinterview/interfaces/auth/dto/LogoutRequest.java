package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("退出登录请求")
public class LogoutRequest {

    @ApiModelProperty(value = "登录令牌，不传时使用请求头 Authorization")
    private String token;
}
