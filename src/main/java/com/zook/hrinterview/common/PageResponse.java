package com.zook.hrinterview.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

@ApiModel("分页响应")
public class PageResponse<T> {

    @ApiModelProperty(value = "当前页数据", required = true)
    private List<T> records;

    @ApiModelProperty(value = "总条数", required = true, example = "100")
    private Long total;

    @ApiModelProperty(value = "页码", required = true, example = "1")
    private Long pageNo;

    @ApiModelProperty(value = "每页数量", required = true, example = "20")
    private Long pageSize;

    public PageResponse() {
    }

    public PageResponse(List<T> records, Long total, Long pageNo, Long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getPageNo() {
        return pageNo;
    }

    public void setPageNo(Long pageNo) {
        this.pageNo = pageNo;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }
}
