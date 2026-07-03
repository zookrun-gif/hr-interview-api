package com.zook.hrinterview.interfaces.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("RBAC角色权限关联实体")
@TableName("rbac_role_permission")
public class RbacRolePermission {

    @ApiModelProperty(value = "关联ID", required = true, example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    private Long roleId;

    @ApiModelProperty(value = "权限ID", required = true, example = "1")
    private Long permissionId;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
