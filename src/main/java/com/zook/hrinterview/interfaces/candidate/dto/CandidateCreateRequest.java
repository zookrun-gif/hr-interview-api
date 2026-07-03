package com.zook.hrinterview.interfaces.candidate.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel("创建候选人请求")
public class CandidateCreateRequest {

    @ApiModelProperty(value = "绑定岗位 ID", required = true, example = "10001")
    @NotNull
    private Long jobId;

    @ApiModelProperty(value = "候选人姓名", required = true, example = "张三")
    @NotBlank
    private String name;

    @ApiModelProperty(value = "性别：MALE 男，FEMALE 女，UNKNOWN 未知", example = "MALE")
    private String gender;

    @ApiModelProperty(value = "年龄", example = "28")
    @Min(0)
    @Max(120)
    private Integer age;

    @ApiModelProperty(value = "手机号", example = "13800000000")
    private String phone;

    @ApiModelProperty(value = "邮箱", example = "candidate@example.com")
    @Email
    private String email;

    @ApiModelProperty(value = "简历文本")
    private String resumeText;
}
