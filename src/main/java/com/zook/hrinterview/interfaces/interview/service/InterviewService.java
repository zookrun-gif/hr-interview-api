package com.zook.hrinterview.interfaces.interview.service;

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

import java.util.List;

public interface InterviewService {

    InterviewDetailResponse create(InterviewCreateRequest request);

    InterviewDetailResponse detail(IdRequest request);

    PageResponse<InterviewDetailResponse> list(InterviewListRequest request);

    InterviewDetailResponse start(IdRequest request);

    InterviewDetailResponse finish(IdRequest request);

    InterviewDetailResponse resetAccessCode(IdRequest request);

    List<InterviewMessageResponse> listMessages(InterviewMessageListRequest request);

    InterviewReportResponse report(IdRequest request);

    PageResponse<InterviewReportListItemResponse> listReports(InterviewReportListRequest request);
}
