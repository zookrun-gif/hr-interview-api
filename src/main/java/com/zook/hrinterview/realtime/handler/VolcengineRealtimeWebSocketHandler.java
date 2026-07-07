package com.zook.hrinterview.realtime.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.config.RealtimeCapacityProperties;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.interfaces.job.entity.EvaluationDimension;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.EvaluationDimensionMapper;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketClientFactory;
import com.zook.hrinterview.realtime.service.RealtimeMessagePersistService;
import com.zook.hrinterview.realtime.service.RealtimeTicketService;
import com.zook.hrinterview.realtime.session.VolcengineRealtimeSession;
import com.zook.hrinterview.interfaces.setting.service.AiInterviewSettingService;
import com.zook.hrinterview.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import javax.annotation.Resource;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VolcengineRealtimeWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VolcengineRealtimeWebSocketHandler.class);

    private static final String REMEMBER_ONLINE_SESSION_SCRIPT = """
            local sessionSocketKey = KEYS[1]
            local onlineSetKey = KEYS[2]
            local sessionId = ARGV[1]
            local socketId = ARGV[2]
            local sessionSocketTtlSeconds = tonumber(ARGV[3])
            local onlineSetTtlSeconds = tonumber(ARGV[4])
            local maxOnlineSessions = tonumber(ARGV[5])
            local currentSocketId = redis.call('GET', sessionSocketKey)
            if currentSocketId and currentSocketId == socketId then
                redis.call('EXPIRE', sessionSocketKey, sessionSocketTtlSeconds)
                redis.call('EXPIRE', onlineSetKey, onlineSetTtlSeconds)
                return 1
            end
            if not currentSocketId then
                if maxOnlineSessions and maxOnlineSessions > 0 and redis.call('SCARD', onlineSetKey) >= maxOnlineSessions then
                    return 0
                end
            end
            redis.call('SET', sessionSocketKey, socketId, 'EX', sessionSocketTtlSeconds)
            redis.call('SADD', onlineSetKey, sessionId)
            redis.call('EXPIRE', onlineSetKey, onlineSetTtlSeconds)
            return 1
            """;

    private final Map<String, VolcengineRealtimeSession> sessions = new ConcurrentHashMap<>();

    @Resource
    private RealtimeTicketService realtimeTicketService;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private RealtimeMessagePersistService realtimeMessagePersistService;

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private EvaluationDimensionMapper evaluationDimensionMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private VolcengineRealtimeProperties properties;

    @Resource
    private AiInterviewSettingService aiInterviewSettingService;

    @Resource
    private RealtimeCapacityProperties realtimeCapacityProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private RealtimeModelWebSocketClientFactory webSocketClientFactory;

    @Resource
    private ApplicationEventPublisher eventPublisher;

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
            List<EvaluationDimension> evaluationDimensions = evaluationDimensionMapper.selectList(
                    Wrappers.lambdaQuery(EvaluationDimension.class)
                            .eq(EvaluationDimension::getJobId, interviewSession.getJobId())
                            .orderByAsc(EvaluationDimension::getId));
            AiInterviewBoundaryConfig boundaryConfig = aiInterviewSettingService.currentBoundaryConfig();
            Candidate candidate = candidateMapper.selectById(interviewSession.getCandidateId());
            VolcengineRealtimeSession realtimeSession = new VolcengineRealtimeSession(
                    properties,
                    objectMapper,
                    session,
                    interviewSession,
                    job,
                    boundaryConfig,
                    evaluationDimensions,
                    candidate,
                    interviewMessageMapper,
                    realtimeMessagePersistService,
                    redisUtils,
                    webSocketClientFactory,
                    eventPublisher
            );
            closeExistingSession(interviewSession.getId(), session.getId());
            rememberOnlineSession(interviewSession.getId(), session.getId());
            sessions.put(session.getId(), realtimeSession);
            try {
                realtimeSession.connect();
                safeSendText(session, "{\"event\":\"connected\"}", "connected");
            } catch (Exception ex) {
                sessions.remove(session.getId());
                clearOnlineSession(interviewSession.getId(), session.getId());
                realtimeSession.close();
                throw ex;
            }
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) {
                if (ErrorCode.TOO_MANY_REQUESTS.equals(businessException.getErrorCode())) {
                    log.info("Realtime WebSocket queued, sessionId={}, message={}", session.getId(), businessException.getMessage());
                } else {
                    log.warn("Realtime WebSocket 建立失败, sessionId={}, code={}, message={}",
                            session.getId(), businessException.getErrorCode().getCode(), businessException.getMessage());
                }
                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("event", "error");
                errorBody.put("code", businessException.getErrorCode().getCode());
                errorBody.put("message", businessException.getMessage());
                safeSendText(session, objectMapper.writeValueAsString(errorBody), "error");
            } else {
                log.error("Realtime WebSocket 建立失败", ex);
                safeSendText(session, "{\"event\":\"error\",\"message\":\"Realtime 连接失败\"}", "error");
            }
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
            safeSendText(session, "{\"event\":\"pong\"}", "pong");
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
            Long interviewSessionId = realtimeSession.getInterviewSessionId();
            if (interviewSessionId != null) {
                clearOnlineSession(interviewSessionId, session.getId());
            }
            realtimeSession.close();
        }
    }

    private void closeExistingSession(Long interviewSessionId, String newSocketId) {
        if (interviewSessionId == null) {
            return;
        }
        String oldSocketId = getOnlineSocketId(interviewSessionId);
        if (!StringUtils.hasText(oldSocketId) || oldSocketId.equals(newSocketId)) {
            return;
        }
        VolcengineRealtimeSession oldSession = sessions.remove(oldSocketId);
        if (oldSession != null) {
            oldSession.closeBrowser("同一面试已在新页面连接");
        }
        clearOnlineSession(interviewSessionId, oldSocketId);
    }

    public void closeInterviewSession(Long interviewSessionId, String reason) {
        if (interviewSessionId == null) {
            return;
        }
        String socketId = getOnlineSocketId(interviewSessionId);
        if (!StringUtils.hasText(socketId)) {
            return;
        }
        VolcengineRealtimeSession realtimeSession = sessions.remove(socketId);
        if (realtimeSession != null) {
            realtimeSession.closeBrowser(StringUtils.hasText(reason) ? reason : "面试已由后台结束");
        }
        clearOnlineSession(interviewSessionId, socketId);
        log.info("Realtime WebSocket closed by admin, interviewSessionId={}, socketId={}", interviewSessionId, socketId);
    }

    private void rememberOnlineSession(Long interviewSessionId, String socketId) {
        if (interviewSessionId == null || !StringUtils.hasText(socketId)) {
            return;
        }
        pruneExpiredOnlineSessions();
        Integer maxOnlineSessions = realtimeCapacityProperties.getMaxOnlineSessions();
        Long result = redisUtils.executeLongScript(
                REMEMBER_ONLINE_SESSION_SCRIPT,
                java.util.List.of(
                        RedisKeyEnum.INTERVIEW_REALTIME_SESSION_SOCKET.buildKey(interviewSessionId),
                        RedisKeyEnum.INTERVIEW_REALTIME_ONLINE_SESSIONS.getKey()
                ),
                String.valueOf(interviewSessionId),
                socketId,
                String.valueOf(RedisKeyEnum.INTERVIEW_REALTIME_SESSION_SOCKET.getTtl().getSeconds()),
                String.valueOf(RedisKeyEnum.INTERVIEW_REALTIME_ONLINE_SESSIONS.getTtl().getSeconds()),
                String.valueOf(maxOnlineSessions == null ? 0 : maxOnlineSessions)
        );
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "当前实时语音人数较多，已进入排队，请保持页面打开");
        }
    }

    private void clearOnlineSession(Long interviewSessionId, String socketId) {
        if (interviewSessionId == null) {
            return;
        }
        String currentSocketId = getOnlineSocketId(interviewSessionId);
        if (!StringUtils.hasText(socketId) || socketId.equals(currentSocketId)) {
            redisUtils.delete(RedisKeyEnum.INTERVIEW_REALTIME_SESSION_SOCKET, interviewSessionId);
            redisUtils.sStringRemove(RedisKeyEnum.INTERVIEW_REALTIME_ONLINE_SESSIONS, String.valueOf(interviewSessionId));
        }
    }

    private String getOnlineSocketId(Long interviewSessionId) {
        if (interviewSessionId == null) {
            return "";
        }
        String value = redisUtils.getString(RedisKeyEnum.INTERVIEW_REALTIME_SESSION_SOCKET, interviewSessionId);
        return value == null ? "" : value;
    }

    private void pruneExpiredOnlineSessions() {
        Set<String> onlineSessionIds = redisUtils.sStringMembers(RedisKeyEnum.INTERVIEW_REALTIME_ONLINE_SESSIONS);
        if (onlineSessionIds == null || onlineSessionIds.isEmpty()) {
            return;
        }
        for (String sessionId : onlineSessionIds) {
            if (!StringUtils.hasText(sessionId)) {
                continue;
            }
            String socketId = redisUtils.getString(RedisKeyEnum.INTERVIEW_REALTIME_SESSION_SOCKET, sessionId);
            if (socketId == null) {
                redisUtils.sStringRemove(RedisKeyEnum.INTERVIEW_REALTIME_ONLINE_SESSIONS, sessionId);
            }
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

    private boolean safeSendText(WebSocketSession session, String payload, String event) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                    return true;
                }
            }
        } catch (Exception ex) {
            log.info("Realtime WebSocket send skipped, sessionId={}, event={}, reason={}",
                    session.getId(), event, ex.getMessage());
        }
        return false;
    }
}
