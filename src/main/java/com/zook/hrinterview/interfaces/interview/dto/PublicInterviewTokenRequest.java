package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("公开面试令牌请求")
public class PublicInterviewTokenRequest {

    @ApiModelProperty(value = "面试邀请令牌", required = true)
    @NotBlank
    private String token;
}
