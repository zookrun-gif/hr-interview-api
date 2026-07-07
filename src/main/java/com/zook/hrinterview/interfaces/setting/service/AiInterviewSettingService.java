package com.zook.hrinterview.interfaces.setting.service;

import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingResponse;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingUpdateRequest;

public interface AiInterviewSettingService {

    AiInterviewSettingResponse detail();

    AiInterviewSettingResponse update(AiInterviewSettingUpdateRequest request);

    AiInterviewBoundaryConfig currentBoundaryConfig();
}
