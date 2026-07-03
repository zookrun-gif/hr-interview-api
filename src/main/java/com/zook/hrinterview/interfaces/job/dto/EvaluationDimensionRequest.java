package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@ApiModel("评分维度请求")
public class EvaluationDimensionRequest {

    @ApiModelProperty(value = "维度名称", required = true, example = "专业能力")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "维度说明")
    private String description;

    @ApiModelProperty(value = "维度权重", required = true, example = "30")
    @NotNull
    private BigDecimal weight;
}
