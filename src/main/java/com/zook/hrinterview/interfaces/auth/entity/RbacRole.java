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
@ApiModel("RBAC角色实体")
@TableName("rbac_role")
public class RbacRole {

    @ApiModelProperty(value = "角色ID", required = true, example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "角色编码", required = true, example = "ADMIN")
    private String code;

    @ApiModelProperty(value = "角色名称", required = true, example = "管理员")
    private String name;

    @ApiModelProperty(value = "角色说明")
    private String description;

    @ApiModelProperty(value = "角色状态：ENABLED启用，DISABLED禁用", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间", required = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
