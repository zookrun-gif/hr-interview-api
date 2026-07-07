package com.zook.hrinterview.realtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("实时面试消息持久化重试任务")
public class RealtimeMessagePersistRetryItem {

    @ApiModelProperty(value = "操作类型：UPSERT", required = true, example = "UPSERT")
    private String operation;

    @ApiModelProperty(value = "面试消息 ID，新增失败时可能为空", example = "10001")
    private Long id;

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色", required = true, example = "AI")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "音频地址")
    private String audioUrl;

    @ApiModelProperty(value = "消息顺序号", required = true, example = "1")
    private Integer sequenceNo;

    @ApiModelProperty(value = "创建时间文本，格式 yyyy-MM-dd HH:mm:ss")
    private String createdAt;

    @ApiModelProperty(value = "当前重试次数", required = true, example = "1")
    private Integer retryCount;

    @ApiModelProperty(value = "最后一次失败原因")
    private String lastError;
}
