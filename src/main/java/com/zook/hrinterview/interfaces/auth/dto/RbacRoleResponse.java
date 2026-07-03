package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("RBAC角色响应")
public class RbacRoleResponse {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "角色编码", required = true, example = "ADMIN")
    private String code;

    @ApiModelProperty(value = "角色名称", required = true, example = "管理员")
    private String name;

    @ApiModelProperty(value = "角色说明")
    private String description;

    @ApiModelProperty(value = "角色状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "创建时间", required = true)
    private LocalDateTime createdAt;
}
