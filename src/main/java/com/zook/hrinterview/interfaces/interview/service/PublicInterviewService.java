package com.zook.hrinterview.interfaces.interview.service;

import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageResponse;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewEnterRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewTokenRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectResponse;

import java.util.List;

public interface PublicInterviewService {

    InterviewDetailResponse detail(PublicInterviewTokenRequest request);

    InterviewDetailResponse enter(PublicInterviewEnterRequest request);

    InterviewDetailResponse finish(PublicInterviewEnterRequest request);

    RealtimeConnectResponse connectRealtime(RealtimeConnectRequest request);

    List<InterviewMessageResponse> listMessages(PublicInterviewMessageListRequest request);
}
