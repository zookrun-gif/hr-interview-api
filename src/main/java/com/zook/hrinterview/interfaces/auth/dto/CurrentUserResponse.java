package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Set;

@Data
@ApiModel("当前用户响应")
public class CurrentUserResponse {

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "用户姓名", required = true, example = "招聘专员")
    private String name;

    @ApiModelProperty(value = "邮箱", required = true, example = "hr@example.com")
    private String email;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    private String mobile;

    @ApiModelProperty(value = "企业微信成员UserID")
    private String wecomUserid;

    @ApiModelProperty(value = "头像地址")
    private String avatar;

    @ApiModelProperty(value = "用户角色", required = true, example = "HR")
    private String role;

    @ApiModelProperty(value = "用户状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "权限编码集合", required = true)
    private Set<String> permissionCodes;
}
