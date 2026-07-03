package com.zook.hrinterview.interfaces.auth.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ApiModel("RBAC保存用户角色请求")
public class RbacUserRoleSaveRequest {

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    @NotNull
    private Long userId;

    @ApiModelProperty(value = "角色ID集合", required = true)
    private List<Long> roleIds;
}
