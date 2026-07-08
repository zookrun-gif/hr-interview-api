package com.zook.hrinterview.interfaces.setting.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import com.zook.hrinterview.component.ai.interview.InterviewReportAiProperties;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingResponse;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingUpdateRequest;
import com.zook.hrinterview.interfaces.setting.entity.AiInterviewSetting;
import com.zook.hrinterview.interfaces.setting.mapper.AiInterviewSettingMapper;
import com.zook.hrinterview.interfaces.setting.service.AiInterviewSettingService;
import com.zook.hrinterview.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Slf4j
@Service
public class AiInterviewSettingServiceImpl extends ServiceImpl<AiInterviewSettingMapper, AiInterviewSetting> implements AiInterviewSettingService {

    private static final Long GLOBAL_SETTING_ID = 1L;
    private static final String DEFAULT_CANDIDATE_QUESTION_ANSWER_GUIDE = "试岗期/试岗：7天\n作息时间/上下班/大小周/休息时间：大小周，9:30 到 18:00\n工资发放时间/发薪日/几号发工资：每月18号\n薪资结构/工资/底薪/提成/业绩/奖金/薪酬：由线下面试 HR 进一步沟通确认，不回答金额、比例或规则\n试用期/福利/调休/加班/社保/公积金：以 HR 后续正式沟通为准";

    @Resource
    private VolcengineRealtimeProperties volcengineRealtimeProperties;

    @Resource
    private InterviewReportAiProperties interviewReportAiProperties;

    @Resource
    private RedisUtils redisUtils;

    @Override
    public AiInterviewSettingResponse detail() {
        return getCachedOrLoad();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiInterviewSettingResponse update(AiInterviewSettingUpdateRequest request) {
        validate(request);
        AiInterviewSetting setting = new AiInterviewSetting();
        setting.setId(GLOBAL_SETTING_ID);
        fillSetting(setting, request);
        saveOrUpdate(setting);
        AiInterviewSettingResponse response = toResponse(setting);
        cacheSetting(response);
        return response;
    }

    @Override
    public AiInterviewBoundaryConfig currentBoundaryConfig() {
        try {
            return getCachedOrLoad();
        } catch (Exception ex) {
            log.warn("Load AI interview setting failed, fallback to yaml defaults", ex);
            return toResponse(defaultSetting());
        }
    }

    private AiInterviewSettingResponse getCachedOrLoad() {
        AiInterviewSettingResponse cached = getCachedSetting();
        if (cached != null) {
            return cached;
        }
        AiInterviewSettingResponse response = toResponse(getOrCreateSetting());
        cacheSetting(response);
        return response;
    }

    private AiInterviewSettingResponse getCachedSetting() {
        try {
            return redisUtils.get(RedisKeyEnum.AI_INTERVIEW_SETTING, AiInterviewSettingResponse.class);
        } catch (Exception ex) {
            log.warn("AI interview setting cache invalid, reload from database, reason={}", summarizeException(ex));
            redisUtils.delete(RedisKeyEnum.AI_INTERVIEW_SETTING);
            return null;
        }
    }

    private void cacheSetting(AiInterviewSettingResponse response) {
        redisUtils.set(RedisKeyEnum.AI_INTERVIEW_SETTING, response);
    }

    private AiInterviewSetting getOrCreateSetting() {
        AiInterviewSetting setting = getById(GLOBAL_SETTING_ID);
        if (setting != null) {
            return setting;
        }
        setting = defaultSetting();
        save(setting);
        return setting;
    }

    private AiInterviewSetting defaultSetting() {
        AiInterviewSetting setting = new AiInterviewSetting();
        setting.setId(GLOBAL_SETTING_ID);
        setting.setTargetQuestionCount(positiveOrDefault(volcengineRealtimeProperties.getTargetQuestionCount(), 8));
        setting.setMaxQuestionCount(positiveOrDefault(volcengineRealtimeProperties.getMaxQuestionCount(), 12));
        setting.setClosingFollowUpTurnLimit(nonNegativeOrDefault(volcengineRealtimeProperties.getClosingFollowUpTurnLimit(), 1));
        setting.setMaxFollowUpPerTopic(positiveOrDefault(volcengineRealtimeProperties.getMaxFollowUpPerTopic(), 2));
        setting.setMinEffectiveAnswerCount(positiveOrDefault(interviewReportAiProperties.getMinEffectiveAnswerCount(), 2));
        setting.setInsufficientAnswerMaxScore(positiveOrDefault(interviewReportAiProperties.getInsufficientAnswerMaxScore(), 60));
        setting.setNoEvidenceMaxScore(positiveOrDefault(interviewReportAiProperties.getNoEvidenceMaxScore(), 70));
        setting.setWeakJobMatchMaxScore(positiveOrDefault(interviewReportAiProperties.getWeakJobMatchMaxScore(), 74));
        setting.setWeakAnswerMaxScore(positiveOrDefault(interviewReportAiProperties.getWeakAnswerMaxScore(), 59));
        setting.setCandidateQuestionAnswerGuide(DEFAULT_CANDIDATE_QUESTION_ANSWER_GUIDE);
        return setting;
    }

    private void validate(AiInterviewSettingUpdateRequest request) {
        if (request.getCandidateQuestionAnswerGuide() != null && request.getCandidateQuestionAnswerGuide().length() > 2000) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "候选人反问回答口径不能超过2000字");
        }
        if (request.getMaxQuestionCount() < request.getTargetQuestionCount()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "最大提问数量不能小于目标提问数量");
        }
        if (request.getWeakAnswerMaxScore() > request.getInsufficientAnswerMaxScore()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "回答有效性不足封顶分不能高于有效回答不足封顶分");
        }
        if (request.getInsufficientAnswerMaxScore() > request.getNoEvidenceMaxScore()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "有效回答不足封顶分不能高于无证据封顶分");
        }
    }

    private void fillSetting(AiInterviewSetting setting, AiInterviewBoundaryConfig source) {
        setting.setTargetQuestionCount(source.getTargetQuestionCount());
        setting.setMaxQuestionCount(source.getMaxQuestionCount());
        setting.setClosingFollowUpTurnLimit(source.getClosingFollowUpTurnLimit());
        setting.setMaxFollowUpPerTopic(source.getMaxFollowUpPerTopic());
        setting.setMinEffectiveAnswerCount(source.getMinEffectiveAnswerCount());
        setting.setInsufficientAnswerMaxScore(source.getInsufficientAnswerMaxScore());
        setting.setNoEvidenceMaxScore(source.getNoEvidenceMaxScore());
        setting.setWeakJobMatchMaxScore(source.getWeakJobMatchMaxScore());
        setting.setWeakAnswerMaxScore(source.getWeakAnswerMaxScore());
        setting.setCandidateQuestionAnswerGuide(defaultQuestionAnswerGuide(source.getCandidateQuestionAnswerGuide()));
    }

    private AiInterviewSettingResponse toResponse(AiInterviewSetting setting) {
        AiInterviewSettingResponse response = new AiInterviewSettingResponse();
        response.setTargetQuestionCount(setting.getTargetQuestionCount());
        response.setMaxQuestionCount(setting.getMaxQuestionCount());
        response.setClosingFollowUpTurnLimit(nonNegativeOrDefault(setting.getClosingFollowUpTurnLimit(), 1));
        response.setMaxFollowUpPerTopic(setting.getMaxFollowUpPerTopic());
        response.setMinEffectiveAnswerCount(setting.getMinEffectiveAnswerCount());
        response.setInsufficientAnswerMaxScore(setting.getInsufficientAnswerMaxScore());
        response.setNoEvidenceMaxScore(setting.getNoEvidenceMaxScore());
        response.setWeakJobMatchMaxScore(setting.getWeakJobMatchMaxScore());
        response.setWeakAnswerMaxScore(setting.getWeakAnswerMaxScore());
        response.setCandidateQuestionAnswerGuide(defaultQuestionAnswerGuide(setting.getCandidateQuestionAnswerGuide()));
        return response;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private String defaultQuestionAnswerGuide(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_CANDIDATE_QUESTION_ANSWER_GUIDE;
    }

    private String summarizeException(Exception ex) {
        if (ex == null) {
            return "";
        }
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (!StringUtils.hasText(message)) {
            return root.getClass().getSimpleName();
        }
        String summary = root.getClass().getSimpleName() + ": " + message.replaceAll("\\s+", " ").trim();
        return summary.length() <= 300 ? summary : summary.substring(0, 300) + "...";
    }
}
