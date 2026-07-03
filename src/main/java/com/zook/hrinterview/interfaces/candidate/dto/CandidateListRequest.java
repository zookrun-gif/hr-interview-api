package com.zook.hrinterview.interfaces.candidate.dto;

import com.zook.hrinterview.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("候选人列表请求")
public class CandidateListRequest extends PageRequest {

    @ApiModelProperty(value = "搜索关键字")
    private String keyword;

    @ApiModelProperty(value = "绑定岗位 ID", example = "10001")
    private Long jobId;
}
