package com.zook.hrinterview.interfaces.setting.controller;

import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingResponse;
import com.zook.hrinterview.interfaces.setting.dto.AiInterviewSettingUpdateRequest;
import com.zook.hrinterview.interfaces.setting.service.AiInterviewSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "AI 面试配置")
@RestController
@RequestMapping("/api/settings/ai-interview")
public class AiInterviewSettingController {

    @Resource
    private AiInterviewSettingService aiInterviewSettingService;

    @ApiOperation("查询 AI 面试配置")
    @PostMapping("/detail")
    public ApiResponse<AiInterviewSettingResponse> detail() {
        return ApiResponse.success(aiInterviewSettingService.detail());
    }

    @ApiOperation("更新 AI 面试配置")
    @PostMapping("/update")
    public ApiResponse<AiInterviewSettingResponse> update(@Valid @RequestBody AiInterviewSettingUpdateRequest request) {
        return ApiResponse.success(aiInterviewSettingService.update(request));
    }
}
