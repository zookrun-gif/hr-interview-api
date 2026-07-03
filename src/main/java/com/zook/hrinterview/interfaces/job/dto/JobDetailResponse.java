package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel("岗位详情响应")
public class JobDetailResponse {

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    private Long id;

    @ApiModelProperty(value = "岗位名称", required = true, example = "Java 后端工程师")
    private String title;

    @ApiModelProperty(value = "岗位 JD", required = true)
    private String jd;

    @ApiModelProperty(value = "能力要求")
    private String requirements;

    @ApiModelProperty(value = "岗位状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "评分维度列表")
    private List<EvaluationDimensionResponse> dimensions;

    @ApiModelProperty(value = "创建时间", required = true)
    private LocalDateTime createdAt;
}
