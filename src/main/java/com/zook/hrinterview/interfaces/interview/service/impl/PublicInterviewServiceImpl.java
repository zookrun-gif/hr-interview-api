package com.zook.hrinterview.interfaces.interview.service.impl;

import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import com.zook.hrinterview.interfaces.interview.service.PublicInterviewService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageResponse;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewEnterRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.PublicInterviewTokenRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectRequest;
import com.zook.hrinterview.interfaces.interview.dto.RealtimeConnectResponse;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.realtime.service.RealtimeTicketService;
import com.zook.hrinterview.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

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

    @Resource
    private RedisUtils redisUtils;

    @Value("${app.interview.invite-valid-days:7}")
    private Integer inviteValidDays;

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

    @Override
    public List<InterviewMessageResponse> listMessages(PublicInterviewMessageListRequest request) {
        InterviewSession session = mustGetByToken(request.getToken());
        verifyAccessCode(session, request.getAccessCode());
        InterviewMessageListRequest messageRequest = new InterviewMessageListRequest();
        messageRequest.setSessionId(session.getId());
        return interviewService.listMessages(messageRequest);
    }

    private InterviewSession mustGetByToken(String token) {
        InterviewSession session = null;
        Object cachedSessionId = redisUtils.get(RedisKeyEnum.INTERVIEW_PUBLIC_TOKEN, token);
        if (cachedSessionId != null) {
            session = interviewSessionMapper.selectById(Long.valueOf(String.valueOf(cachedSessionId)));
        }
        if (session == null) {
            session = interviewSessionMapper.selectOne(
                    Wrappers.lambdaQuery(InterviewSession.class).eq(InterviewSession::getInviteToken, token));
            if (session != null) {
                redisUtils.set(RedisKeyEnum.INTERVIEW_PUBLIC_TOKEN, token, session.getId());
            }
        }
        if (session == null) {
            redisUtils.delete(RedisKeyEnum.INTERVIEW_PUBLIC_TOKEN, token);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试链接不存在或已失效");
        }
        if (InterviewStatus.EXPIRED.equals(session.getStatus())) {
            redisUtils.delete(RedisKeyEnum.INTERVIEW_PUBLIC_TOKEN, token);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试链接不存在或已失效");
        }
        if (expireInviteIfNeeded(session)) {
            redisUtils.delete(RedisKeyEnum.INTERVIEW_PUBLIC_TOKEN, token);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试链接不存在或已失效");
        }
        return session;
    }

    private boolean expireInviteIfNeeded(InterviewSession session) {
        if (session == null || !isInvitePendingStatus(session.getStatus())) {
            return false;
        }
        LocalDateTime expiresAt = resolveInviteExpiresAt(session);
        if (expiresAt == null || !LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        session.setStatus(InterviewStatus.EXPIRED);
        session.setInviteExpiresAt(expiresAt);
        interviewSessionMapper.update(null, Wrappers.lambdaUpdate(InterviewSession.class)
                .eq(InterviewSession::getId, session.getId())
                .in(InterviewSession::getStatus, InterviewStatus.INVITED, InterviewStatus.WAITING)
                .set(InterviewSession::getStatus, InterviewStatus.EXPIRED)
                .set(InterviewSession::getInviteExpiresAt, expiresAt));
        return true;
    }

    private boolean isInvitePendingStatus(String status) {
        return InterviewStatus.INVITED.equals(status) || InterviewStatus.WAITING.equals(status);
    }

    private LocalDateTime resolveInviteExpiresAt(InterviewSession session) {
        if (session == null) {
            return null;
        }
        if (session.getInviteExpiresAt() != null) {
            return session.getInviteExpiresAt();
        }
        LocalDateTime baseTime = session.getCreatedAt() == null ? LocalDateTime.now() : session.getCreatedAt();
        return baseTime.plusDays(inviteValidDays());
    }

    private int inviteValidDays() {
        return inviteValidDays == null || inviteValidDays <= 0 ? 7 : inviteValidDays;
    }

    private void verifyAccessCode(InterviewSession session, String accessCode) {
        String cacheData = accessCacheData(session, accessCode);
        Object cachedSessionId = redisUtils.get(RedisKeyEnum.INTERVIEW_PUBLIC_ACCESS, cacheData);
        if (cachedSessionId != null && String.valueOf(session.getId()).equals(String.valueOf(cachedSessionId))) {
            return;
        }
        if (!StringUtils.hasText(session.getAccessCodeHash()) || !passwordEncoder.matches(accessCode, session.getAccessCodeHash())) {
            redisUtils.delete(RedisKeyEnum.INTERVIEW_PUBLIC_ACCESS, cacheData);
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_CODE_INVALID);
        }
        redisUtils.set(RedisKeyEnum.INTERVIEW_PUBLIC_ACCESS, cacheData, session.getId());
    }

    private String accessCacheData(InterviewSession session, String accessCode) {
        String raw = session.getId() + ":" + session.getAccessCodeHash() + ":" + accessCode;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return raw;
        }
    }
}
