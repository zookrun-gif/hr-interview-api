package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("RBAC权限响应")
public class RbacPermissionResponse {

    @ApiModelProperty(value = "权限ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "父级权限ID，根节点为0", required = true, example = "0")
    private Long parentId;

    @ApiModelProperty(value = "权限编码", required = true, example = "job:list")
    private String code;

    @ApiModelProperty(value = "前端权限标识", example = "jobs")
    private String permissionKey;

    @ApiModelProperty(value = "权限名称", required = true, example = "查询岗位列表")
    private String name;

    @ApiModelProperty(value = "权限类型", required = true, example = "API")
    private String type;

    @ApiModelProperty(value = "资源路径", example = "/api/jobs/list")
    private String resourcePath;

    @ApiModelProperty(value = "前端组件或页面标识", example = "jobs")
    private String component;

    @ApiModelProperty(value = "权限说明")
    private String description;

    @ApiModelProperty(value = "排序号", required = true, example = "100")
    private Integer sortNo;

    @ApiModelProperty(value = "权限状态", required = true, example = "ENABLED")
    private String status;
}
