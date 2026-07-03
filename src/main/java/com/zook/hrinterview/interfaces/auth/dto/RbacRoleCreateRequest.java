package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("RBAC创建角色请求")
public class RbacRoleCreateRequest {

    @ApiModelProperty(value = "角色编码", required = true, example = "INTERVIEWER")
    @NotBlank
    private String code;

    @ApiModelProperty(value = "角色名称", required = true, example = "面试官")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "角色说明")
    private String description;

    @ApiModelProperty(value = "角色状态：ENABLED启用，DISABLED禁用", required = true, example = "ENABLED")
    @NotBlank
    private String status;
}
