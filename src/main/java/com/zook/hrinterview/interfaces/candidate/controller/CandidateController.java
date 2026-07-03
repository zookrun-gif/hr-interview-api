package com.zook.hrinterview.interfaces.candidate.controller;

import com.zook.hrinterview.interfaces.candidate.dto.CandidateCreateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateDetailResponse;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateListRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateUpdateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.ResumeParseResponse;
import com.zook.hrinterview.interfaces.candidate.service.CandidateService;
import com.zook.hrinterview.common.ApiResponse;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "候选人管理")
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    @Resource
    private CandidateService candidateService;

    @ApiOperation("创建候选人")
    @PostMapping("/create")
    public ApiResponse<CandidateDetailResponse> create(@Valid @RequestBody CandidateCreateRequest request) {
        return ApiResponse.success(candidateService.create(request));
    }

    @ApiOperation("更新候选人")
    @PostMapping("/update")
    public ApiResponse<CandidateDetailResponse> update(@Valid @RequestBody CandidateUpdateRequest request) {
        return ApiResponse.success(candidateService.update(request));
    }

    @ApiOperation("删除候选人")
    @PostMapping("/delete")
    public ApiResponse<Boolean> delete(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(candidateService.delete(request));
    }

    @ApiOperation("查询候选人详情")
    @PostMapping("/detail")
    public ApiResponse<CandidateDetailResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(candidateService.detail(request));
    }

    @ApiOperation("查询候选人列表")
    @PostMapping("/list")
    public ApiResponse<PageResponse<CandidateDetailResponse>> list(@Valid @RequestBody CandidateListRequest request) {
        return ApiResponse.success(candidateService.list(request));
    }

    @ApiOperation("解析 PDF 简历")
    @PostMapping("/resume/parse-pdf")
    public ApiResponse<ResumeParseResponse> parseResumePdf(MultipartFile file) {
        return ApiResponse.success(candidateService.parseResumePdf(file));
    }
}
