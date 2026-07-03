package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel("公开面试 Realtime 连接请求")
public class RealtimeConnectRequest {

    @ApiModelProperty(value = "面试邀请令牌", required = true)
    @NotBlank
    private String token;

    @ApiModelProperty(value = "面试访问口令", required = true, example = "123456")
    @NotBlank
    private String accessCode;
}
