package com.zook.hrinterview.interfaces.interview.entity;

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
@ApiModel("面试会话实体")
@TableName("interview_session")
public class InterviewSession {

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "候选人 ID", required = true, example = "10001")
    private Long candidateId;

    @ApiModelProperty(value = "面试状态", required = true, example = "INVITED")
    private String status;

    @ApiModelProperty(value = "邀请令牌", required = true)
    private String inviteToken;

    @ApiModelProperty(value = "邀请链接过期时间")
    private LocalDateTime inviteExpiresAt;

    @ApiModelProperty(value = "面试访问口令哈希")
    private String accessCodeHash;

    @ApiModelProperty(value = "开始时间")
    private LocalDateTime startedAt;

    @ApiModelProperty(value = "结束时间")
    private LocalDateTime endedAt;

    @ApiModelProperty(value = "失败原因")
    private String failReason;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间", required = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
