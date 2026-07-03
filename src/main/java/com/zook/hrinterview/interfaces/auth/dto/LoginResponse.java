package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("登录响应")
public class LoginResponse {

    @ApiModelProperty(value = "访问令牌", required = true)
    private String token;

    @ApiModelProperty(value = "令牌类型", required = true, example = "Bearer")
    private String tokenType;

    @ApiModelProperty(value = "过期秒数", required = true, example = "86400")
    private Long expiresIn;

    @ApiModelProperty(value = "当前用户信息", required = true)
    private CurrentUserResponse user;
}
