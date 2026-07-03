package com.zook.hrinterview.interfaces.interview.service.impl;

import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import com.zook.hrinterview.interfaces.interview.service.PublicInterviewService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewEnterRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewTokenRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectResponse;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.realtime.RealtimeTicketService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Service
public class PublicInterviewServiceImpl implements PublicInterviewService {

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private InterviewService interviewService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private RealtimeTicketService realtimeTicketService;

    @Resource
    private VolcengineRealtimeProperties volcengineRealtimeProperties;

    @Override
    public InterviewDetailResponse detail(PublicInterviewTokenRequest request) {
        InterviewSession session = mustGetByToken(request.getToken());
        IdRequest idRequest = new IdRequest();
        idRequest.setId(session.getId());
        return interviewService.detail(idRequest);
    }

    @Override
    public InterviewDetailResponse enter(PublicInterviewEnterRequest request) {
        InterviewSession session = mustGetByToken(request.getToken());
        verifyAccessCode(session, request.getAccessCode());
        if (InterviewStatus.IN_PROGRESS.equals(session.getStatus())) {
            IdRequest idRequest = new IdRequest();
            idRequest.setId(session.getId());
            return interviewService.detail(idRequest);
        }
        if (!InterviewStatus.INVITED.equals(session.getStatus()) && !InterviewStatus.WAITING.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID);
        }
        IdRequest idRequest = new IdRequest();
        idRequest.setId(session.getId());
        return interviewService.start(idRequest);
    }

    @Override
    public InterviewDetailResponse finish(PublicInterviewEnterRequest request) {
        InterviewSession session = mustGetByToken(request.getToken());
        verifyAccessCode(session, request.getAccessCode());
        IdRequest idRequest = new IdRequest();
        idRequest.setId(session.getId());
        return interviewService.finish(idRequest);
    }

    @Override
    public RealtimeConnectResponse connectRealtime(RealtimeConnectRequest request) {
        InterviewSession session = mustGetByToken(request.getToken());
        verifyAccessCode(session, request.getAccessCode());
        if (!InterviewStatus.IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID, "请先进入面试后再连接实时语音");
        }
        RealtimeConnectResponse response = new RealtimeConnectResponse();
        response.setTicket(realtimeTicketService.createTicket(session.getId()));
        response.setWebsocketUrl("/ws/public/interviews/realtime");
        response.setResourceId(volcengineRealtimeProperties.getResourceId());
        response.setAudioFormat(volcengineRealtimeProperties.getInputAudioFormat());
        return response;
    }

    private InterviewSession mustGetByToken(String token) {
        InterviewSession session = interviewSessionMapper.selectOne(
                Wrappers.lambdaQuery(InterviewSession.class).eq(InterviewSession::getInviteToken, token));
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试链接不存在或已失效");
        }
        return session;
    }

    private void verifyAccessCode(InterviewSession session, String accessCode) {
        if (!StringUtils.hasText(session.getAccessCodeHash()) || !passwordEncoder.matches(accessCode, session.getAccessCodeHash())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_CODE_INVALID);
        }
    }
}
