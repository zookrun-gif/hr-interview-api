package com.zook.hrinterview.interfaces.candidate.service;

import com.zook.hrinterview.interfaces.candidate.dto.CandidateCreateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateDetailResponse;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateListRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateUpdateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.ResumeParseResponse;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CandidateService {

    CandidateDetailResponse create(CandidateCreateRequest request);

    CandidateDetailResponse update(CandidateUpdateRequest request);

    Boolean delete(IdRequest request);

    CandidateDetailResponse detail(IdRequest request);

    PageResponse<CandidateDetailResponse> list(CandidateListRequest request);

    ResumeParseResponse parseResumePdf(MultipartFile file);
}
