package com.zook.hrinterview.interfaces.job.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel("岗位实体")
@TableName("job_position")
public class JobPosition {

    @ApiModelProperty(value = "岗位 ID", required = true, example = "10001")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "岗位名称", required = true, example = "Java 后端工程师")
    private String title;

    @ApiModelProperty(value = "岗位 JD", required = true)
    private String jd;

    @ApiModelProperty(value = "能力要求")
    private String requirements;

    @ApiModelProperty(value = "岗位状态", required = true, example = "ENABLED")
    private String status;

    @ApiModelProperty(value = "创建人 ID", required = true, example = "1")
    private Long createdBy;

    @ApiModelProperty(value = "创建时间", required = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间", required = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
