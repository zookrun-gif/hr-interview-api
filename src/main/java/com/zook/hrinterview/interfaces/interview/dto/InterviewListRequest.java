package com.zook.hrinterview.interfaces.interview.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("面试会话列表请求")
public class InterviewListRequest extends PageRequest {

    @ApiModelProperty(value = "岗位 ID", example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "候选人 ID", example = "10001")
    private Long candidateId;

    @ApiModelProperty(value = "面试状态", example = "INVITED")
    private String status;

    @ApiModelProperty(value = "创建时间开始", example = "2026-07-07T00:00:00")
    private LocalDateTime createdAtStart;

    @ApiModelProperty(value = "创建时间结束", example = "2026-07-07T23:59:59")
    private LocalDateTime createdAtEnd;

    @ApiModelProperty(value = "完成时间开始", example = "2026-07-07T00:00:00")
    private LocalDateTime endedAtStart;

    @ApiModelProperty(value = "完成时间结束", example = "2026-07-07T23:59:59")
    private LocalDateTime endedAtEnd;
}
