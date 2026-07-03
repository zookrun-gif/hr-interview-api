package com.zook.hrinterview.interfaces.candidate.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("候选人详情响应")
public class CandidateDetailResponse {

    @ApiModelProperty(value = "候选人 ID", required = true, example = "10001")
    private Long id;

    @ApiModelProperty(value = "绑定岗位 ID", required = true, example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "绑定岗位名称", required = true, example = "Java 后端工程师")
    private String jobTitle;

    @ApiModelProperty(value = "候选人姓名", required = true, example = "张三")
    private String name;

    @ApiModelProperty(value = "性别：MALE 男，FEMALE 女，UNKNOWN 未知", example = "MALE")
    private String gender;

    @ApiModelProperty(value = "年龄", example = "28")
    private Integer age;

    @ApiModelProperty(value = "手机号", example = "13800000000")
    private String phone;

    @ApiModelProperty(value = "邮箱", example = "candidate@example.com")
    private String email;

    @ApiModelProperty(value = "简历文本")
    private String resumeText;

    @ApiModelProperty(value = "简历文件地址")
    private String resumeFileUrl;

    @ApiModelProperty(value = "创建时间", required = true)
    private LocalDateTime createdAt;
}
