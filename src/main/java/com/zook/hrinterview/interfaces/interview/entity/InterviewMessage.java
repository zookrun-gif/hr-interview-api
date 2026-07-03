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
@ApiModel("面试消息实体")
@TableName("interview_message")
public class InterviewMessage {

    @ApiModelProperty(value = "面试消息 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色", required = true, example = "AI")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "音频地址")
    private String audioUrl;

    @ApiModelProperty(value = "消息顺序", required = true, example = "1")
    private Integer sequenceNo;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
