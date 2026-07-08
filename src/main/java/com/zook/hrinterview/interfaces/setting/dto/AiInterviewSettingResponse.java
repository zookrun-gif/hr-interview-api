package com.zook.hrinterview.interfaces.setting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import io.swagger.annotations.ApiModel;

@ApiModel("AI 面试配置响应")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiInterviewSettingResponse extends AiInterviewBoundaryConfig {
}
