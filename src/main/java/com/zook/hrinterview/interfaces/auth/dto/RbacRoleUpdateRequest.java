package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("RBAC更新角色请求")
public class RbacRoleUpdateRequest {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "角色名称", required = true, example = "招聘人员")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "角色说明")
    private String description;

    @ApiModelProperty(value = "角色状态：ENABLED启用，DISABLED禁用", required = true, example = "ENABLED")
    @NotBlank
    private String status;
}
