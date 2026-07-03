package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("RBAC用户响应")
public class RbacUserResponse {

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "用户姓名", required = true, example = "管理员")
    private String name;

    @ApiModelProperty(value = "邮箱", required = true, example = "admin@example.com")
    private String email;

    @ApiModelProperty(value = "旧角色字段", required = true, example = "ADMIN")
    private String role;

    @ApiModelProperty(value = "用户状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "绑定角色ID集合", required = true)
    private List<Long> roleIds;

    @ApiModelProperty(value = "绑定角色名称集合", required = true)
    private List<String> roleNames;
}
