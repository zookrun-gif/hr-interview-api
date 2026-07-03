package com.zook.hrinterview.realtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VolcengineRealtimeWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VolcengineRealtimeWebSocketHandler.class);

    private final Map<String, VolcengineRealtimeSession> sessions = new ConcurrentHashMap<>();

    @Resource
    private RealtimeTicketService realtimeTicketService;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private VolcengineRealtimeProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            ensureConfigured();
            Long sessionId = realtimeTicketService.consumeTicket(readTicket(session.getUri()));
            InterviewSession interviewSession = interviewSessionMapper.selectById(sessionId);
            if (interviewSession == null || !InterviewStatus.IN_PROGRESS.equals(interviewSession.getStatus())) {
                throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID, "面试状态不允许连接实时语音");
            }
            JobPosition job = jobPositionMapper.selectById(interviewSession.getJobId());
            Candidate candidate = candidateMapper.selectById(interviewSession.getCandidateId());
            VolcengineRealtimeSession realtimeSession = new VolcengineRealtimeSession(
                    properties,
                    objectMapper,
                    session,
                    interviewSession,
                    job,
                    candidate,
                    interviewMessageMapper
            );
            sessions.put(session.getId(), realtimeSession);
            realtimeSession.connect();
            session.sendMessage(new TextMessage("{\"event\":\"connected\"}"));
        } catch (Exception ex) {
            log.error("Realtime WebSocket 建立失败", ex);
            session.sendMessage(new TextMessage("{\"event\":\"error\",\"message\":\"Realtime 连接失败\"}"));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        VolcengineRealtimeSession realtimeSession = sessions.get(session.getId());
        if (realtimeSession == null) {
            return;
        }
        byte[] bytes = new byte[message.getPayload().remaining()];
        message.getPayload().get(bytes);
        realtimeSession.sendAudio(bytes);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        VolcengineRealtimeSession realtimeSession = sessions.get(session.getId());
        if (realtimeSession == null) {
            return;
        }
        if ("__ping".equalsIgnoreCase(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("{\"event\":\"pong\"}"));
            } catch (Exception ex) {
                log.warn("Realtime heartbeat pong send failed, sessionId={}", session.getId(), ex);
            }
            return;
        }
        if ("finish".equalsIgnoreCase(message.getPayload())) {
            realtimeSession.finish();
            return;
        }
        String payload = message.getPayload();
        if (payload != null && payload.startsWith("audio:")) {
            try {
                realtimeSession.sendAudio(Base64.getDecoder().decode(payload.substring("audio:".length())));
            } catch (IllegalArgumentException ex) {
                log.warn("Realtime audio base64 decode failed, sessionId={}", session.getId());
            }
            return;
        }
        realtimeSession.chatTextQuery(payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Browser Realtime WebSocket closed, sessionId={}, code={}, reason={}", session.getId(), status.getCode(), status.getReason());
        VolcengineRealtimeSession realtimeSession = sessions.remove(session.getId());
        if (realtimeSession != null) {
            realtimeSession.close();
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getWsUrl())
                || !StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getResourceId())
                || !StringUtils.hasText(properties.getAppKey())) {
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "火山 Realtime 模型配置未完成");
        }
    }

    private String readTicket(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getQuery())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Realtime 连接票据不能为空");
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "ticket".equals(parts[0]) && StringUtils.hasText(parts[1])) {
                return parts[1];
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Realtime 连接票据不能为空");
    }
}
