package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("面试会话详情响应")
public class InterviewDetailResponse {

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    private Long id;

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "岗位名称", example = "Java 后端工程师")
    private String jobTitle;

    @ApiModelProperty(value = "候选人 ID", required = true, example = "10001")
    private Long candidateId;

    @ApiModelProperty(value = "候选人姓名", example = "张三")
    private String candidateName;

    @ApiModelProperty(value = "面试状态", required = true, example = "INVITED")
    private String status;

    @ApiModelProperty(value = "邀请令牌", required = true)
    private String inviteToken;

    @ApiModelProperty(value = "面试链接", required = true)
    private String inviteUrl;

    @ApiModelProperty(value = "邀请链接过期时间")
    private LocalDateTime inviteExpiresAt;

    @ApiModelProperty(value = "面试访问口令，仅创建或重置口令时返回")
    private String accessCode;

    @ApiModelProperty(value = "是否已生成面试访问口令", required = true)
    private Boolean hasAccessCode;

    @ApiModelProperty(value = "开始时间")
    private LocalDateTime startedAt;

    @ApiModelProperty(value = "结束时间")
    private LocalDateTime endedAt;

    @ApiModelProperty(value = "创建时间", required = true)
    private LocalDateTime createdAt;
}
