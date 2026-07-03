package com.zook.hrinterview.interfaces.job.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ApiModel("更新岗位请求")
public class JobUpdateRequest {

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "岗位名称", required = true, example = "Java 后端工程师")
    @NotBlank
    private String title;

    @ApiModelProperty(value = "岗位 JD", required = true)
    @NotBlank
    private String jd;

    @ApiModelProperty(value = "能力要求")
    private String requirements;

    @ApiModelProperty(value = "岗位状态：ENABLED 启用，DISABLED 停用", required = true, example = "ENABLED")
    @NotBlank
    private String status;

    @ApiModelProperty(value = "评分维度列表")
    @Valid
    private List<EvaluationDimensionRequest> dimensions;
}
