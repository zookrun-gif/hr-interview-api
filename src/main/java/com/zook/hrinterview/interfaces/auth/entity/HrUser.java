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
@ApiModel("HR用户实体")
@TableName("hr_user")
public class HrUser {

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    @TableId(type = IdType.AUTO)
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

    @ApiModelProperty(value = "密码哈希", required = true)
    private String passwordHash;

    @ApiModelProperty(value = "用户角色", required = true, example = "HR")
    private String role;

    @ApiModelProperty(value = "用户状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间", required = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
