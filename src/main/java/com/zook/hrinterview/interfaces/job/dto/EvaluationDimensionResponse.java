package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("评分维度响应")
public class EvaluationDimensionResponse {

    @ApiModelProperty(value = "评分维度 ID", required = true, example = "10001")
    private Long id;

    @ApiModelProperty(value = "维度名称", required = true, example = "专业能力")
    private String name;

    @ApiModelProperty(value = "维度说明")
    private String description;

    @ApiModelProperty(value = "维度权重", required = true, example = "30")
    private BigDecimal weight;
}
