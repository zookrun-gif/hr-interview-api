package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Data
@ApiModel("登录请求")
public class LoginRequest {

    @ApiModelProperty(value = "邮箱", required = true, example = "admin@example.com")
    @Email
    @NotBlank
    private String email;

    @ApiModelProperty(value = "密码", required = true, example = "Admin@123456")
    @NotBlank
    private String password;
}
