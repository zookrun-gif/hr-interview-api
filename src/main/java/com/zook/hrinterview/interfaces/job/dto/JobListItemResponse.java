package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("岗位列表项响应")
public class JobListItemResponse {

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    private Long id;

    @ApiModelProperty(value = "岗位名称", required = true, example = "Java 后端工程师")
    private String title;

    @ApiModelProperty(value = "岗位 JD 摘要")
    private String jdSummary;

    @ApiModelProperty(value = "能力要求摘要")
    private String requirementsSummary;

    @ApiModelProperty(value = "岗位状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "创建时间", required = true)
    private LocalDateTime createdAt;
}
