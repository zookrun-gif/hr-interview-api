package com.zook.hrinterview.component.ai.interview;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.interview-report")
public class InterviewReportAiProperties {

    @ApiModelProperty(value = "是否启用 AI 生成面试报告；false 时使用后端规则兜底报告", example = "true")
    private Boolean enabled = false;

    @ApiModelProperty(value = "发送给 AI 生成报告的最大内容长度；越大越全面，但耗时和 token 消耗越高", example = "18000")
    private Integer maxContentChars = 18000;

    @ApiModelProperty(value = "最低有效回答轮次；低于该值时按低可信面试处理并触发分数封顶", example = "2")
    private Integer minEffectiveAnswerCount = 2;

    @ApiModelProperty(value = "有效回答轮次不足时的最高总分；防止只答一两句被 AI 误判高分", example = "60")
    private Integer insufficientAnswerMaxScore = 60;

    @ApiModelProperty(value = "没有真实案例、流程、工具、数据或处理细节时的最高总分", example = "70")
    private Integer noEvidenceMaxScore = 70;

    @ApiModelProperty(value = "岗位匹配度低于阈值时的最高总分；用于防止核心岗位能力不足却总分过高", example = "74")
    private Integer weakJobMatchMaxScore = 74;

    @ApiModelProperty(value = "回答有效性明显不足时的最高总分；用于处理答非所问、泛泛而谈等情况", example = "59")
    private Integer weakAnswerMaxScore = 59;
}
