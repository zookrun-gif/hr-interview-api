package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@ApiModel("RBAC创建用户请求")
public class RbacUserCreateRequest {

    @ApiModelProperty(value = "用户姓名", required = true, example = "招聘专员")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "登录邮箱", required = true, example = "hr@example.com")
    @Email
    @NotBlank
    private String email;

    @ApiModelProperty(value = "初始密码，8到64位", required = true, example = "Password123")
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @ApiModelProperty(value = "用户状态：ENABLED启用，DISABLED禁用", required = true, example = "ENABLED")
    @NotBlank
    private String status;

    @ApiModelProperty(value = "绑定角色ID集合")
    private List<Long> roleIds;
}
