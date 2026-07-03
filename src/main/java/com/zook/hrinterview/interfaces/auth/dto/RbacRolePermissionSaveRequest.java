package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ApiModel("RBAC保存角色权限请求")
public class RbacRolePermissionSaveRequest {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    @NotNull
    private Long roleId;

    @ApiModelProperty(value = "权限ID集合", required = true)
    private List<Long> permissionIds;
}
