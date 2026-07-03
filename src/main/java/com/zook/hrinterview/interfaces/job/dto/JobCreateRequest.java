package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@ApiModel("创建岗位请求")
public class JobCreateRequest {

    @ApiModelProperty(value = "岗位名称", required = true, example = "Java 后端工程师")
    @NotBlank
    private String title;

    @ApiModelProperty(value = "岗位 JD", required = true)
    @NotBlank
    private String jd;

    @ApiModelProperty(value = "能力要求")
    private String requirements;

    @ApiModelProperty(value = "评分维度列表")
    @Valid
    private List<EvaluationDimensionRequest> dimensions;
}
