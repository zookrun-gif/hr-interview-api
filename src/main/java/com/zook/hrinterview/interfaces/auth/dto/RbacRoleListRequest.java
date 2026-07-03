package com.zook.hrinterview.interfaces.auth.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("RBAC角色列表请求")
public class RbacRoleListRequest extends PageRequest {

    @ApiModelProperty(value = "关键字：角色编码或角色名称")
    private String keyword;

    @ApiModelProperty(value = "角色状态：ENABLED启用，DISABLED禁用")
    private String status;
}
