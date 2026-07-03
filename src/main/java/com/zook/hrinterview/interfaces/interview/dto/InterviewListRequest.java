package com.zook.hrinterview.interfaces.interview.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
