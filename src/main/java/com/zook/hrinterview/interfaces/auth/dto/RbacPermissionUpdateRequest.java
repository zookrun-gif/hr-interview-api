package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("RBAC更新菜单权限请求")
public class RbacPermissionUpdateRequest {

    @ApiModelProperty(value = "权限ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "父级权限ID，根节点为0", required = true, example = "0")
    @NotNull
    private Long parentId;

    @ApiModelProperty(value = "前端权限标识", example = "system")
    private String permissionKey;

    @ApiModelProperty(value = "权限名称", required = true, example = "系统管理")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "权限类型：MENU菜单，BUTTON按钮，API接口", required = true, example = "MENU")
    @NotBlank
    private String type;

    @ApiModelProperty(value = "资源路径", example = "/api/rbac/roles/list")
    private String resourcePath;

    @ApiModelProperty(value = "前端组件或页面标识", example = "rbacRole")
    private String component;

    @ApiModelProperty(value = "权限说明")
    private String description;

    @ApiModelProperty(value = "排序号", required = true, example = "100")
    @NotNull
    private Integer sortNo;

    @ApiModelProperty(value = "权限状态：ENABLED启用，DISABLED禁用", required = true, example = "ENABLED")
    @NotBlank
    private String status;
}
