package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("RBAC角色权限详情响应")
public class RbacRolePermissionDetailResponse {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    private Long roleId;

    @ApiModelProperty(value = "已绑定权限ID集合", required = true)
    private List<Long> permissionIds;
}
