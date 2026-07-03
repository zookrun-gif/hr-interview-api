package com.zook.hrinterview.interfaces.auth.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("RBAC用户列表请求")
public class RbacUserListRequest extends PageRequest {

    @ApiModelProperty(value = "关键字：用户姓名或邮箱")
    private String keyword;

    @ApiModelProperty(value = "用户状态：ENABLED启用，DISABLED禁用")
    private String status;
}
