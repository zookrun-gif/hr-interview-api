package com.zook.hrinterview.interfaces.job.entity;

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
@ApiModel("评分维度实体")
@TableName("evaluation_dimension")
public class EvaluationDimension {

    @ApiModelProperty(value = "评分维度 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    private Long jobId;

    @ApiModelProperty(value = "维度名称", required = true, example = "专业能力")
    private String name;

    @ApiModelProperty(value = "维度说明")
    private String description;

    @ApiModelProperty(value = "维度权重", required = true, example = "30")
    private BigDecimal weight;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
