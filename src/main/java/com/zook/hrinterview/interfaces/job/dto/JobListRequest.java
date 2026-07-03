package com.zook.hrinterview.interfaces.job.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("岗位列表请求")
public class JobListRequest extends PageRequest {

    @ApiModelProperty(value = "搜索关键字")
    private String keyword;

    @ApiModelProperty(value = "岗位状态", example = "ENABLED")
    private String status;
}
