package com.zook.hrinterview.interfaces.interview.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("面试报告列表请求")
public class InterviewReportListRequest extends PageRequest {

    @ApiModelProperty(value = "关键字，支持候选人姓名、岗位名称", example = "Java")
    private String keyword;

    @ApiModelProperty(value = "推荐结果", example = "RECOMMEND")
    private String recommendation;
}
