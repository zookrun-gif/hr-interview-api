package com.zook.hrinterview.interfaces.interview.controller;

import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewCreateRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportListItemResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportResponse;
import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "面试管理")
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    @Resource
    private InterviewService interviewService;

    @ApiOperation("创建面试会话")
    @PostMapping("/create")
    public ApiResponse<InterviewDetailResponse> create(@Valid @RequestBody InterviewCreateRequest request) {
        return ApiResponse.success(interviewService.create(request));
    }

    @ApiOperation("查询面试会话详情")
    @PostMapping("/detail")
    public ApiResponse<InterviewDetailResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(interviewService.detail(request));
    }

    @ApiOperation("查询面试会话列表")
    @PostMapping("/list")
    public ApiResponse<PageResponse<InterviewDetailResponse>> list(@Valid @RequestBody InterviewListRequest request) {
        return ApiResponse.success(interviewService.list(request));
    }

    @ApiOperation("重置面试访问口令")
    @PostMapping("/access-code/reset")
    public ApiResponse<InterviewDetailResponse> resetAccessCode(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(interviewService.resetAccessCode(request));
    }

    @ApiOperation("结束面试并生成报告")
    @PostMapping("/finish")
    public ApiResponse<InterviewDetailResponse> finish(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(interviewService.finish(request));
    }

    @ApiOperation("查询面试消息")
    @PostMapping("/messages/list")
    public ApiResponse<List<InterviewMessageResponse>> listMessages(@Valid @RequestBody InterviewMessageListRequest request) {
        return ApiResponse.success(interviewService.listMessages(request));
    }

    @ApiOperation("查询面试报告")
    @PostMapping("/reports/detail")
    public ApiResponse<InterviewReportResponse> report(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(interviewService.report(request));
    }

    @ApiOperation("查询面试报告列表")
    @PostMapping("/reports/list")
    public ApiResponse<PageResponse<InterviewReportListItemResponse>> listReports(@Valid @RequestBody InterviewReportListRequest request) {
        return ApiResponse.success(interviewService.listReports(request));
    }
}
