package com.zook.hrinterview.interfaces.candidate.entity;

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
@ApiModel("候选人实体")
@TableName("candidate")
public class Candidate {

    @ApiModelProperty(value = "候选人 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "绑定岗位 ID", required = true, example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "候选人姓名", required = true, example = "张三")
    private String name;

    @ApiModelProperty(value = "性别：MALE 男，FEMALE 女，UNKNOWN 未知", example = "MALE")
    private String gender;

    @ApiModelProperty(value = "年龄", example = "28")
    private Integer age;

    @ApiModelProperty(value = "手机号", example = "13800000000")
    private String phone;

    @ApiModelProperty(value = "邮箱", example = "candidate@example.com")
    private String email;

    @ApiModelProperty(value = "简历文本")
    private String resumeText;

    @ApiModelProperty(value = "简历文件地址")
    private String resumeFileUrl;

    @ApiModelProperty(value = "创建人 ID", required = true, example = "1")
    private Long createdBy;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间", required = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
