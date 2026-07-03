package com.zook.hrinterview.interfaces.job.controller;

import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.job.dto.JobCreateRequest;
import com.zook.hrinterview.interfaces.job.dto.JobDetailResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListItemResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListRequest;
import com.zook.hrinterview.interfaces.job.dto.JobUpdateRequest;
import com.zook.hrinterview.interfaces.job.service.JobService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "岗位管理")
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    @Resource
    private JobService jobService;

    @ApiOperation("创建岗位")
    @PostMapping("/create")
    public ApiResponse<JobDetailResponse> create(@Valid @RequestBody JobCreateRequest request) {
        return ApiResponse.success(jobService.create(request));
    }

    @ApiOperation("更新岗位")
    @PostMapping("/update")
    public ApiResponse<JobDetailResponse> update(@Valid @RequestBody JobUpdateRequest request) {
        return ApiResponse.success(jobService.update(request));
    }

    @ApiOperation("删除岗位")
    @PostMapping("/delete")
    public ApiResponse<Boolean> delete(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(jobService.delete(request));
    }

    @ApiOperation("查询岗位详情")
    @PostMapping("/detail")
    public ApiResponse<JobDetailResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(jobService.detail(request));
    }

    @ApiOperation("查询岗位列表")
    @PostMapping("/list")
    public ApiResponse<PageResponse<JobListItemResponse>> list(@Valid @RequestBody JobListRequest request) {
        return ApiResponse.success(jobService.list(request));
    }
}
