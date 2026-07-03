package com.zook.hrinterview.interfaces.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("面试报告实体")
@TableName("interview_report")
public class InterviewReport {

    @ApiModelProperty(value = "面试报告 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "面试会话 ID", required = true, example = "10001")
    private Long sessionId;

    @ApiModelProperty(value = "总分", required = true, example = "82")
    private BigDecimal totalScore;

    @ApiModelProperty(value = "各维度评分 JSON")
    private String dimensionScoresJson;

    @ApiModelProperty(value = "优势")
    private String strengths;

    @ApiModelProperty(value = "风险点")
    private String risks;

    @ApiModelProperty(value = "推荐结果", required = true, example = "RECOMMEND")
    private String recommendation;

    @ApiModelProperty(value = "推荐追问问题")
    private String followUpQuestions;

    @ApiModelProperty(value = "原始报告 JSON")
    private String rawReportJson;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
