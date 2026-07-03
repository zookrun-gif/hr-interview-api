package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@ApiModel("RBAC角色权限详情请求")
public class RbacRolePermissionDetailRequest {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    @NotNull
    private Long roleId;
}
