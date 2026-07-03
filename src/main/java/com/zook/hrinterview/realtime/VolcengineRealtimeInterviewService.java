package com.zook.hrinterview.realtime;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VolcengineRealtimeInterviewService implements RealtimeInterviewService {

    private final VolcengineRealtimeProperties properties;

    public VolcengineRealtimeInterviewService(VolcengineRealtimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void startInterview(Long sessionId) {
        ensureConfigured();
    }

    @Override
    public void receiveCandidateAudio(Long sessionId, byte[] audioChunk) {
        ensureConfigured();
    }

    @Override
    public void finishInterview(Long sessionId) {
        ensureConfigured();
    }

    @Override
    public void closeInterview(Long sessionId) {
        ensureConfigured();
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getResourceId())
                || !StringUtils.hasText(properties.getAppKey())) {
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "Realtime 模型配置未完成");
        }
    }
}
