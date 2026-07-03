package com.zook.hrinterview.interfaces.candidate.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("简历解析响应")
public class ResumeParseResponse {

    @ApiModelProperty(value = "解析后的纯文本内容", required = true)
    private String plainText;

    @ApiModelProperty(value = "解析后的富文本 HTML 内容", required = true)
    private String htmlContent;

    @ApiModelProperty(value = "原始文件名")
    private String fileName;

    @ApiModelProperty(value = "解析出的候选人姓名")
    private String name;

    @ApiModelProperty(value = "解析出的性别：MALE 男，FEMALE 女，UNKNOWN 未知")
    private String gender;

    @ApiModelProperty(value = "解析出的年龄")
    private Integer age;

    @ApiModelProperty(value = "解析出的手机号")
    private String phone;

    @ApiModelProperty(value = "解析出的邮箱")
    private String email;

    @ApiModelProperty(value = "是否使用 AI 完成结构化解析")
    private Boolean aiParsed;

    @ApiModelProperty(value = "AI 解析置信度")
    private Double aiConfidence;

    @ApiModelProperty(value = "AI 结构化解析 JSON")
    private String structuredJson;
}
