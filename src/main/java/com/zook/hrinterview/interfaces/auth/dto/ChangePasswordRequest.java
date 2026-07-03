package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@ApiModel("修改密码请求")
public class ChangePasswordRequest {

    @ApiModelProperty(value = "原密码", required = true, example = "Admin@123456")
    @NotBlank
    private String oldPassword;

    @ApiModelProperty(value = "新密码，至少 8 位", required = true, example = "NewAdmin@123456")
    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;

    @ApiModelProperty(value = "确认新密码", required = true, example = "NewAdmin@123456")
    @NotBlank
    @Size(min = 8, max = 64)
    private String confirmPassword;
}
