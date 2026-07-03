package com.zook.hrinterview.interfaces.job.service;

import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.job.dto.JobCreateRequest;
import com.zook.hrinterview.interfaces.job.dto.JobDetailResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListItemResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListRequest;
import com.zook.hrinterview.interfaces.job.dto.JobUpdateRequest;

public interface JobService {

    JobDetailResponse create(JobCreateRequest request);

    JobDetailResponse update(JobUpdateRequest request);

    Boolean delete(IdRequest request);

    JobDetailResponse detail(IdRequest request);

    PageResponse<JobListItemResponse> list(JobListRequest request);
}
