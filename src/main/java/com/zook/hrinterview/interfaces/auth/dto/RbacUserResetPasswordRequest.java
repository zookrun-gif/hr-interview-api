package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@ApiModel("RBAC重置用户密码请求")
public class RbacUserResetPasswordRequest {

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    @NotNull
    private Long userId;

    @ApiModelProperty(value = "新密码，8到64位", required = true, example = "Password123")
    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;
}
