package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("面试报告列表项响应")
public class InterviewReportListItemResponse {

    @ApiModelProperty(value = "面试报告 ID", required = true, example = "10001")
    private Long reportId;

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    private Long sessionId;

    @ApiModelProperty(value = "岗位 ID", example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "岗位名称", example = "Java 后端工程师")
    private String jobTitle;

    @ApiModelProperty(value = "候选人 ID", example = "10001")
    private Long candidateId;

    @ApiModelProperty(value = "候选人姓名", example = "张三")
    private String candidateName;

    @ApiModelProperty(value = "总分", example = "82.50")
    private BigDecimal totalScore;

    @ApiModelProperty(value = "推荐结果", example = "RECOMMEND")
    private String recommendation;

    @ApiModelProperty(value = "面试开始时间")
    private LocalDateTime startedAt;

    @ApiModelProperty(value = "面试结束时间")
    private LocalDateTime endedAt;

    @ApiModelProperty(value = "报告创建时间")
    private LocalDateTime reportCreatedAt;
}
