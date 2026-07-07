package com.zook.hrinterview.component.ai.interview;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("AI 面试边界配置")
public class AiInterviewBoundaryConfig {

    @ApiModelProperty(value = "目标提问数量；AI 接近该数量后开始收尾", required = true, example = "8")
    private Integer targetQuestionCount;

    @ApiModelProperty(value = "最大提问数量；达到后后端会硬性阻止继续追问", required = true, example = "12")
    private Integer maxQuestionCount;

    @ApiModelProperty(value = "达到最大提问数后，允许候选人补充说明或反问的轮次数；0 表示答完最后一个正式问题后直接自动结束", required = true, example = "1")
    private Integer closingFollowUpTurnLimit;

    @ApiModelProperty(value = "同一能力点或同一项目连续追问上限", required = true, example = "2")
    private Integer maxFollowUpPerTopic;

    @ApiModelProperty(value = "最低有效回答轮次；低于该值时按低可信面试处理并触发分数封顶", required = true, example = "2")
    private Integer minEffectiveAnswerCount;

    @ApiModelProperty(value = "有效回答轮次不足时的最高总分", required = true, example = "60")
    private Integer insufficientAnswerMaxScore;

    @ApiModelProperty(value = "没有真实案例、流程、工具、数据或处理细节时的最高总分", required = true, example = "70")
    private Integer noEvidenceMaxScore;

    @ApiModelProperty(value = "岗位匹配度低于阈值时的最高总分", required = true, example = "74")
    private Integer weakJobMatchMaxScore;

    @ApiModelProperty(value = "回答有效性明显不足时的最高总分", required = true, example = "59")
    private Integer weakAnswerMaxScore;

    @ApiModelProperty(value = "候选人反问回答口径；候选人询问试用期、作息、休息、福利、薪资等公司制度时只能按该内容回答，未配置的信息需提示以 HR 最终沟通为准")
    private String candidateQuestionAnswerGuide;
}
