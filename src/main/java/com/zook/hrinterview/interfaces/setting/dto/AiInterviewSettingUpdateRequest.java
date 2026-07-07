package com.zook.hrinterview.interfaces.setting.dto;

import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import io.swagger.annotations.ApiModel;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@ApiModel("更新 AI 面试配置请求")
public class AiInterviewSettingUpdateRequest extends AiInterviewBoundaryConfig {

    @Override
    @NotNull
    @Min(1)
    @Max(50)
    public Integer getTargetQuestionCount() {
        return super.getTargetQuestionCount();
    }

    @Override
    @NotNull
    @Min(1)
    @Max(80)
    public Integer getMaxQuestionCount() {
        return super.getMaxQuestionCount();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(5)
    public Integer getClosingFollowUpTurnLimit() {
        return super.getClosingFollowUpTurnLimit();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(10)
    public Integer getMaxFollowUpPerTopic() {
        return super.getMaxFollowUpPerTopic();
    }

    @Override
    @NotNull
    @Min(1)
    @Max(20)
    public Integer getMinEffectiveAnswerCount() {
        return super.getMinEffectiveAnswerCount();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(100)
    public Integer getInsufficientAnswerMaxScore() {
        return super.getInsufficientAnswerMaxScore();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(100)
    public Integer getNoEvidenceMaxScore() {
        return super.getNoEvidenceMaxScore();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(100)
    public Integer getWeakJobMatchMaxScore() {
        return super.getWeakJobMatchMaxScore();
    }

    @Override
    @NotNull
    @Min(0)
    @Max(100)
    public Integer getWeakAnswerMaxScore() {
        return super.getWeakAnswerMaxScore();
    }
}
