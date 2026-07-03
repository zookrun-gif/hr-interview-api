package com.zook.hrinterview.interfaces.interview.controller;

import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewEnterRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewTokenRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectResponse;
import com.zook.hrinterview.interfaces.interview.service.PublicInterviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "候选人公开面试")
@RestController
@RequestMapping("/api/public/interviews")
public class PublicInterviewController {

    @Resource
    private PublicInterviewService publicInterviewService;

    @ApiOperation("查询公开面试详情")
    @PostMapping("/detail")
    public ApiResponse<InterviewDetailResponse> detail(@Valid @RequestBody PublicInterviewTokenRequest request) {
        return ApiResponse.success(publicInterviewService.detail(request));
    }

    @ApiOperation("进入公开面试")
    @PostMapping("/enter")
    public ApiResponse<InterviewDetailResponse> enter(@Valid @RequestBody PublicInterviewEnterRequest request) {
        return ApiResponse.success(publicInterviewService.enter(request));
    }

    @ApiOperation("结束公开面试")
    @PostMapping("/finish")
    public ApiResponse<InterviewDetailResponse> finish(@Valid @RequestBody PublicInterviewEnterRequest request) {
        return ApiResponse.success(publicInterviewService.finish(request));
    }

    @ApiOperation("连接公开面试 Realtime")
    @PostMapping("/realtime/connect")
    public ApiResponse<RealtimeConnectResponse> connectRealtime(@Valid @RequestBody RealtimeConnectRequest request) {
        return ApiResponse.success(publicInterviewService.connectRealtime(request));
    }
}
