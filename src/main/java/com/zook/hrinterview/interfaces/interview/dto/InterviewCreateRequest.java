package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@ApiModel("创建面试会话请求")
public class InterviewCreateRequest {

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    @NotNull
    private Long jobId;

    @ApiModelProperty(value = "候选人 ID", required = true, example = "10001")
    @NotNull
    private Long candidateId;
}
