package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@ApiModel("面试消息列表请求")
public class InterviewMessageListRequest {

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    @NotNull
    private Long sessionId;
}
