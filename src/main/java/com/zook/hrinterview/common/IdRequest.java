package com.zook.hrinterview.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

@ApiModel("ID 请求")
public class IdRequest {

    @ApiModelProperty(value = "主键 ID", required = true, example = "10001")
    @NotNull
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
