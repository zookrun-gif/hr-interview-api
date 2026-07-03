package com.zook.hrinterview.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@ApiModel("分页请求")
public class PageRequest {

    @ApiModelProperty(value = "页码，从 1 开始", required = true, example = "1")
    @Min(1)
    private Integer pageNo = 1;

    @ApiModelProperty(value = "每页数量", required = true, example = "20")
    @Min(1)
    @Max(200)
    private Integer pageSize = 20;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
