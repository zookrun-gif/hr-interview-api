package com.zook.hrinterview.interfaces.interview.service.impl;

import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.component.ai.interview.InterviewReportAiComponent;
import com.zook.hrinterview.component.ai.interview.InterviewReportAiRequest;
import com.zook.hrinterview.config.InterviewReportQueueProperties;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.dto.InterviewCreateRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewDetailResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportListItemResponse;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewReportResponse;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.entity.InterviewReport;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewReportMapper;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.interfaces.job.entity.EvaluationDimension;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.EvaluationDimensionMapper;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import com.zook.hrinterview.realtime.handler.VolcengineRealtimeWebSocketHandler;
import com.zook.hrinterview.realtime.event.InterviewAutoFinishEvent;
import com.zook.hrinterview.realtime.service.RealtimeMessagePersistService;
import com.zook.hrinterview.utils.InterviewMessageUtils;
import com.zook.hrinterview.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSession> implements InterviewService {

    private static final String STATUS_ENABLED = "ENABLED";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Pattern WEAK_ANSWER_PATTERN = Pattern.compile(
            "^(嗯+|啊+|哦+|好+|有+|没有+|不会+|不知道+|不清楚+|随便|都行|什么都会|no+|hello|hi|你好)[。.!！,，\\s]*$",
            Pattern.CASE_INSENSITIVE);

    private static final String LEGACY_OPENING_MESSAGE = "你好，我是本次 AI 面试官。请先用一分钟介绍一下你自己。";

    private static final String LEGACY_RESUME_CONTROL_MESSAGE = "我们继续刚才的面试。请根据前面的回答继续提问，不要重新要求候选人做自我介绍。";

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private EvaluationDimensionMapper evaluationDimensionMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private InterviewReportMapper interviewReportMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private InterviewReportQueueProperties interviewReportQueueProperties;

    @Resource
    private InterviewReportAiComponent interviewReportAiComponent;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private VolcengineRealtimeWebSocketHandler realtimeWebSocketHandler;

    @Resource
    private RealtimeMessagePersistService realtimeMessagePersistService;

    @PostConstruct
    public void recoverProcessingReportQueue() {
        if (!Boolean.TRUE.equals(interviewReportQueueProperties.getEnabled())
                || !Boolean.TRUE.equals(interviewReportQueueProperties.getRecoverProcessingOnStartup())) {
            return;
        }
        int recoveredCount = 0;
        Object sessionId;
        while ((sessionId = redisUtils.lRightPop(RedisKeyEnum.INTERVIEW_REPORT_PROCESSING_QUEUE)) != null) {
            redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE, sessionId);
            recoveredCount++;
        }
        List<InterviewSession> generatingSessions = list(Wrappers.lambdaQuery(InterviewSession.class)
                .select(InterviewSession::getId)
                .eq(InterviewSession::getStatus, InterviewStatus.GENERATING)
                .orderByAsc(InterviewSession::getEndedAt)
                .last("limit " + normalizeStartupRecoverLimit(interviewReportQueueProperties.getStartupRecoverLimit())));
        for (InterviewSession generatingSession : generatingSessions) {
            if (queueInterviewReportTask(generatingSession.getId())) {
                recoveredCount++;
            }
        }
        if (recoveredCount > 0) {
            log.warn("Recovered processing interview report tasks to pending queue, count={}", recoveredCount);
        }
    }

    @Override
    public InterviewDetailResponse create(InterviewCreateRequest request) {
        JobPosition job = jobPositionMapper.selectById(request.getJobId());
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        if (!STATUS_ENABLED.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "岗位已停用，不能创建面试");
        }
        Candidate candidate = candidateMapper.selectById(request.getCandidateId());
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        if (!request.getJobId().equals(candidate.getJobId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "候选人绑定岗位与当前面试岗位不一致");
        }
        InterviewSession session = new InterviewSession();
        session.setJobId(request.getJobId());
        session.setCandidateId(request.getCandidateId());
        session.setStatus(InterviewStatus.INVITED);
        session.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        String accessCode = generateAccessCode();
        session.setAccessCodeHash(passwordEncoder.encode(accessCode));
        save(session);
        InterviewDetailResponse response = toDetailResponse(session);
        response.setAccessCode(accessCode);
        return response;
    }

    @Override
    public InterviewDetailResponse detail(IdRequest request) {
        return detailById(request.getId());
    }

    @Override
    public PageResponse<InterviewDetailResponse> list(InterviewListRequest request) {
        LambdaQueryWrapper<InterviewSession> wrapper = Wrappers.lambdaQuery(InterviewSession.class)
                .eq(request.getJobId() != null, InterviewSession::getJobId, request.getJobId())
                .eq(request.getCandidateId() != null, InterviewSession::getCandidateId, request.getCandidateId())
                .eq(StringUtils.isNotBlank(request.getStatus()), InterviewSession::getStatus, request.getStatus())
                .orderByDesc(InterviewSession::getCreatedAt);
        Page<InterviewSession> page = page(Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<InterviewSession> sessions = page.getRecords();
        Map<Long, JobPosition> jobMap = batchQueryJobs(sessions);
        Map<Long, Candidate> candidateMap = batchQueryCandidates(sessions);
        List<InterviewDetailResponse> records = page.getRecords().stream()
                .map(session -> toDetailResponse(
                        session,
                        jobMap.get(session.getJobId()),
                        candidateMap.get(session.getCandidateId())
                ))
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public InterviewDetailResponse start(IdRequest request) {
        InterviewSession session = mustGetSession(request.getId());
        if (!InterviewStatus.INVITED.equals(session.getStatus()) && !InterviewStatus.WAITING.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID);
        }
        session.setStatus(InterviewStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        updateById(session);
        return toDetailResponse(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewDetailResponse finish(IdRequest request) {
        InterviewSession session = mustGetSession(request.getId());
        if (!InterviewStatus.IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID);
        }
        session.setStatus(InterviewStatus.GENERATING);
        session.setEndedAt(LocalDateTime.now());
        updateById(session);
        realtimeWebSocketHandler.closeInterviewSession(session.getId(), "面试已由后台结束");
        createEvaluationReportAsync(session);
        return toDetailResponse(session);
    }

    @EventListener
    public void handleInterviewAutoFinish(InterviewAutoFinishEvent event) {
        if (event == null || event.getSessionId() == null) {
            return;
        }
        boolean updated = update(Wrappers.lambdaUpdate(InterviewSession.class)
                .eq(InterviewSession::getId, event.getSessionId())
                .eq(InterviewSession::getStatus, InterviewStatus.IN_PROGRESS)
                .set(InterviewSession::getStatus, InterviewStatus.GENERATING)
                .set(InterviewSession::getEndedAt, LocalDateTime.now()));
        if (!updated) {
            InterviewSession latestSession = getById(event.getSessionId());
            log.info("Skip auto finish because status changed, sessionId={}, status={}",
                    event.getSessionId(), latestSession == null ? null : latestSession.getStatus());
            return;
        }
        InterviewSession session = mustGetSession(event.getSessionId());
        boolean reportQueued = false;
        try {
            createEvaluationReportAsync(session);
            reportQueued = true;
        } catch (Exception ex) {
            log.warn("Interview auto finish report queue failed, sessionId={}, reason={}",
                    event.getSessionId(), event.getReason(), ex);
        }
        log.info("Interview auto finished, sessionId={}, reason={}, reportQueued={}",
                event.getSessionId(), event.getReason(), reportQueued);
    }

    @Override
    public InterviewDetailResponse resetAccessCode(IdRequest request) {
        InterviewSession session = mustGetSession(request.getId());
        if (!InterviewStatus.INVITED.equals(session.getStatus()) && !InterviewStatus.WAITING.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID);
        }
        String accessCode = generateAccessCode();
        session.setAccessCodeHash(passwordEncoder.encode(accessCode));
        updateById(session);
        InterviewDetailResponse response = toDetailResponse(session);
        response.setAccessCode(accessCode);
        return response;
    }

    @Override
    public List<InterviewMessageResponse> listMessages(InterviewMessageListRequest request) {
        mustGetSession(request.getSessionId());
        List<InterviewMessage> messages = interviewMessageMapper.selectList(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .eq(InterviewMessage::getSessionId, request.getSessionId())
                        .orderByAsc(InterviewMessage::getSequenceNo)
                        .orderByAsc(InterviewMessage::getId));
        messages = mergeDbAndBufferedMessages(messages, request.getSessionId());
        return InterviewMessageUtils.mergeAdjacentSameRole(messages).stream()
                .filter(message -> !isSystemOnlyInterviewMessage(message))
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public InterviewReportResponse report(IdRequest request) {
        InterviewReport report = interviewReportMapper.selectOne(
                Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, request.getId()));
        if (report == null || isPlaceholderReport(report)) {
            InterviewSession session = mustGetSession(request.getId());
            if (InterviewStatus.GENERATING.equals(session.getStatus())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试报告生成中，请稍后刷新");
            }
            if (!InterviewStatus.COMPLETED.equals(session.getStatus())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试报告不存在");
            }
            createEvaluationReport(session);
            report = interviewReportMapper.selectOne(
                    Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, request.getId()));
        }
        return toReportResponse(report);
    }

    @Override
    public PageResponse<InterviewReportListItemResponse> listReports(InterviewReportListRequest request) {
        List<Long> matchedSessionIds = queryReportSessionIdsByKeyword(request.getKeyword());
        if (matchedSessionIds != null && matchedSessionIds.isEmpty()) {
            int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
            int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
            return new PageResponse<>(List.of(), 0L, (long) pageNo, (long) pageSize);
        }
        LambdaQueryWrapper<InterviewReport> wrapper = Wrappers.lambdaQuery(InterviewReport.class)
                .eq(StringUtils.isNotBlank(request.getRecommendation()), InterviewReport::getRecommendation, request.getRecommendation())
                .in(matchedSessionIds != null, InterviewReport::getSessionId, matchedSessionIds)
                .orderByDesc(InterviewReport::getCreatedAt);
        Page<InterviewReport> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<InterviewReport> reportPage = interviewReportMapper.selectPage(page, wrapper);
        List<InterviewReport> reports = reportPage.getRecords();
        Map<Long, InterviewSession> sessionMap = batchQuerySessionsByReport(reports);
        Map<Long, JobPosition> jobMap = batchQueryJobs(new ArrayList<>(sessionMap.values()));
        Map<Long, Candidate> candidateMap = batchQueryCandidates(new ArrayList<>(sessionMap.values()));
        List<InterviewReportListItemResponse> records = reports.stream()
                .map(report -> toReportListItem(
                        report,
                        sessionMap.get(report.getSessionId()),
                        sessionMap.get(report.getSessionId()) == null ? null : jobMap.get(sessionMap.get(report.getSessionId()).getJobId()),
                        sessionMap.get(report.getSessionId()) == null ? null : candidateMap.get(sessionMap.get(report.getSessionId()).getCandidateId())
                ))
                .collect(Collectors.toList());
        return new PageResponse<>(
                records,
                reportPage.getTotal(),
                reportPage.getCurrent(),
                reportPage.getSize()
        );
    }

    private List<Long> queryReportSessionIdsByKeyword(String keyword) {
        if (!StringUtils.isNotBlank(keyword)) {
            return null;
        }
        String like = keyword.trim();
        List<Long> jobIds = jobPositionMapper.selectList(
                        Wrappers.lambdaQuery(JobPosition.class)
                                .select(JobPosition::getId)
                                .like(JobPosition::getTitle, like))
                .stream()
                .map(JobPosition::getId)
                .collect(Collectors.toList());
        List<Long> candidateIds = candidateMapper.selectList(
                        Wrappers.lambdaQuery(Candidate.class)
                                .select(Candidate::getId)
                                .like(Candidate::getName, like))
                .stream()
                .map(Candidate::getId)
                .collect(Collectors.toList());
        if (jobIds.isEmpty() && candidateIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<InterviewSession> wrapper = Wrappers.lambdaQuery(InterviewSession.class)
                .select(InterviewSession::getId)
                .and(query -> {
                    boolean hasJobIds = !jobIds.isEmpty();
                    boolean hasCandidateIds = !candidateIds.isEmpty();
                    if (hasJobIds) {
                        query.in(InterviewSession::getJobId, jobIds);
                    }
                    if (hasJobIds && hasCandidateIds) {
                        query.or();
                    }
                    if (hasCandidateIds) {
                        query.in(InterviewSession::getCandidateId, candidateIds);
                    }
                });
        return list(wrapper).stream()
                .map(InterviewSession::getId)
                .collect(Collectors.toList());
    }

    private InterviewSession mustGetSession(Long id) {
        InterviewSession session = getById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    private InterviewDetailResponse detailById(Long id) {
        return toDetailResponse(mustGetSession(id));
    }

    private InterviewDetailResponse toDetailResponse(InterviewSession session) {
        JobPosition job = jobPositionMapper.selectById(session.getJobId());
        Candidate candidate = candidateMapper.selectById(session.getCandidateId());
        return toDetailResponse(session, job, candidate);
    }

    private InterviewDetailResponse toDetailResponse(
            InterviewSession session,
            JobPosition job,
            Candidate candidate
    ) {
        InterviewDetailResponse response = new InterviewDetailResponse();
        response.setId(session.getId());
        response.setJobId(session.getJobId());
        if (job != null) {
            response.setJobTitle(job.getTitle());
        }
        response.setCandidateId(session.getCandidateId());
        if (candidate != null) {
            response.setCandidateName(candidate.getName());
        }
        response.setStatus(session.getStatus());
        response.setInviteToken(session.getInviteToken());
        response.setInviteUrl("/interview/" + session.getInviteToken());
        response.setHasAccessCode(StringUtils.isNotBlank(session.getAccessCodeHash()));
        response.setStartedAt(session.getStartedAt());
        response.setEndedAt(session.getEndedAt());
        response.setCreatedAt(session.getCreatedAt());
        return response;
    }

    private Map<Long, JobPosition> batchQueryJobs(List<InterviewSession> sessions) {
        List<Long> jobIds = sessions.stream()
                .map(InterviewSession::getJobId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, JobPosition> jobMap = new LinkedHashMap<>();
        if (jobIds.isEmpty()) {
            return jobMap;
        }
        jobPositionMapper.selectBatchIds(jobIds).forEach(job -> jobMap.put(job.getId(), job));
        return jobMap;
    }

    private Map<Long, Candidate> batchQueryCandidates(List<InterviewSession> sessions) {
        List<Long> candidateIds = sessions.stream()
                .map(InterviewSession::getCandidateId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Candidate> candidateMap = new LinkedHashMap<>();
        if (candidateIds.isEmpty()) {
            return candidateMap;
        }
        candidateMapper.selectBatchIds(candidateIds).forEach(candidate -> candidateMap.put(candidate.getId(), candidate));
        return candidateMap;
    }

    private Map<Long, InterviewSession> batchQuerySessionsByReport(List<InterviewReport> reports) {
        Set<Long> sessionIds = reports.stream()
                .map(InterviewReport::getSessionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, InterviewSession> sessionMap = new LinkedHashMap<>();
        if (sessionIds.isEmpty()) {
            return sessionMap;
        }
        listByIds(sessionIds).forEach(session -> sessionMap.put(session.getId(), session));
        return sessionMap;
    }

    private String generateAccessCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private void appendMessage(Long sessionId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNo(1);
        interviewMessageMapper.insert(message);
    }

    private List<InterviewMessage> mergeDbAndBufferedMessages(List<InterviewMessage> dbMessages, Long sessionId) {
        Map<Integer, InterviewMessage> messageMap = new LinkedHashMap<>();
        for (InterviewMessage message : dbMessages == null ? List.<InterviewMessage>of() : dbMessages) {
            if (message.getSequenceNo() != null) {
                messageMap.put(message.getSequenceNo(), message);
            }
        }
        List<InterviewMessage> bufferedMessages = realtimeMessagePersistService.listBufferedMessages(sessionId);
        for (InterviewMessage bufferedMessage : bufferedMessages) {
            if (bufferedMessage.getSequenceNo() == null) {
                continue;
            }
            InterviewMessage existing = messageMap.get(bufferedMessage.getSequenceNo());
            if (existing == null || contentLength(bufferedMessage) >= contentLength(existing)) {
                messageMap.put(bufferedMessage.getSequenceNo(), bufferedMessage);
            }
        }
        return messageMap.values().stream()
                .sorted((left, right) -> {
                    int sequenceCompare = Integer.compare(left.getSequenceNo(), right.getSequenceNo());
                    if (sequenceCompare != 0) {
                        return sequenceCompare;
                    }
                    Long leftId = left.getId() == null ? 0L : left.getId();
                    Long rightId = right.getId() == null ? 0L : right.getId();
                    return Long.compare(leftId, rightId);
                })
                .collect(Collectors.toList());
    }

    private int contentLength(InterviewMessage message) {
        return message == null || message.getContent() == null ? 0 : message.getContent().length();
    }

    private boolean isSystemOnlyInterviewMessage(InterviewMessage message) {
        if (message == null || !"AI".equals(message.getRole()) || !StringUtils.isNotBlank(message.getContent())) {
            return false;
        }
        String content = message.getContent().trim();
        return LEGACY_OPENING_MESSAGE.equals(content) || LEGACY_RESUME_CONTROL_MESSAGE.equals(content);
    }

    private void createEvaluationReportAsync(InterviewSession session) {
        if (!Boolean.TRUE.equals(interviewReportQueueProperties.getEnabled())) {
            generateEvaluationReportNow(session.getId());
            return;
        }
        if (queueInterviewReportTask(session.getId())) {
            log.info("Interview report task queued, sessionId={}, pendingSize={}",
                    session.getId(), redisUtils.lSize(RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE));
        }
    }

    private boolean queueInterviewReportTask(Long sessionId) {
        Boolean queued = redisUtils.setIfAbsent(RedisKeyEnum.INTERVIEW_REPORT_QUEUE_FLAG, sessionId, "1");
        if (!Boolean.TRUE.equals(queued)) {
            log.info("Interview report task already queued, sessionId={}", sessionId);
            return false;
        }
        redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE, sessionId);
        return true;
    }

    @Scheduled(fixedDelayString = "${interview.report.queue.poll-interval-millis:3000}")
    public void consumeInterviewReportQueue() {
        if (!Boolean.TRUE.equals(interviewReportQueueProperties.getEnabled())) {
            return;
        }
        int batchSize = normalizeBatchSize(interviewReportQueueProperties.getBatchSize());
        for (int i = 0; i < batchSize; i++) {
            Object rawSessionId = redisUtils.lRightPopAndLeftPush(
                    RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE,
                    RedisKeyEnum.INTERVIEW_REPORT_PROCESSING_QUEUE
            );
            Long sessionId = parseSessionId(rawSessionId);
            if (sessionId == null) {
                return;
            }
            generateQueuedEvaluationReport(sessionId);
        }
    }

    private void generateQueuedEvaluationReport(Long sessionId) {
        try {
            generateEvaluationReportNow(sessionId);
            redisUtils.lRemove(RedisKeyEnum.INTERVIEW_REPORT_PROCESSING_QUEUE, 1, sessionId);
            redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_QUEUE_FLAG, sessionId);
            redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_RETRY_COUNT, sessionId);
        } catch (Exception ex) {
            redisUtils.lRemove(RedisKeyEnum.INTERVIEW_REPORT_PROCESSING_QUEUE, 1, sessionId);
            handleQueuedReportFailure(sessionId, ex);
        }
    }

    private void generateEvaluationReportNow(Long sessionId) {
        String lockValue = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redisUtils.tryLock(RedisKeyEnum.INTERVIEW_REPORT_GENERATE_LOCK, sessionId, lockValue))) {
            log.info("Interview report task locked by another worker, sessionId={}", sessionId);
            return;
        }
        try {
            InterviewSession latestSession = mustGetSession(sessionId);
            if (InterviewStatus.COMPLETED.equals(latestSession.getStatus())) {
                return;
            }
            if (!InterviewStatus.GENERATING.equals(latestSession.getStatus())) {
                log.info("Skip interview report task because status changed, sessionId={}, status={}",
                        sessionId, latestSession.getStatus());
                return;
            }
            createEvaluationReport(latestSession);
            markReportCompleted(sessionId);
        } finally {
            redisUtils.unlock(RedisKeyEnum.INTERVIEW_REPORT_GENERATE_LOCK, sessionId, lockValue);
        }
    }

    private void handleQueuedReportFailure(Long sessionId, Exception ex) {
        Long retryCount = redisUtils.increment(RedisKeyEnum.INTERVIEW_REPORT_RETRY_COUNT, sessionId);
        int maxRetryTimes = normalizeMaxRetryTimes(interviewReportQueueProperties.getMaxRetryTimes());
        if (retryCount != null && retryCount <= maxRetryTimes) {
            redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE, sessionId);
            log.warn("Interview report task failed and requeued, sessionId={}, retry={}/{}",
                    sessionId, retryCount, maxRetryTimes, ex);
            return;
        }
        redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_QUEUE_FLAG, sessionId);
        markReportFailed(sessionId, ex.getMessage());
        log.warn("Interview report task failed permanently, sessionId={}, retry={}",
                sessionId, retryCount, ex);
    }

    private int normalizeBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize <= 0) {
            return 1;
        }
        return Math.min(batchSize, 10);
    }

    private int normalizeMaxRetryTimes(Integer maxRetryTimes) {
        if (maxRetryTimes == null || maxRetryTimes < 0) {
            return 0;
        }
        return Math.min(maxRetryTimes, 10);
    }

    private int normalizeStartupRecoverLimit(Integer startupRecoverLimit) {
        if (startupRecoverLimit == null || startupRecoverLimit <= 0) {
            return 100;
        }
        return Math.min(startupRecoverLimit, 1000);
    }

    private Long parseSessionId(Object rawSessionId) {
        if (rawSessionId == null) {
            return null;
        }
        if (rawSessionId instanceof Number) {
            return ((Number) rawSessionId).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(rawSessionId));
        } catch (NumberFormatException ex) {
            log.warn("Invalid interview report queue sessionId={}", rawSessionId);
            return null;
        }
    }

    private InterviewMessageResponse toMessageResponse(InterviewMessage message) {
        InterviewMessageResponse response = new InterviewMessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setAudioUrl(message.getAudioUrl());
        response.setSequenceNo(message.getSequenceNo());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    public void createEvaluationReport(InterviewSession session) {
        Long sessionId = session.getId();
        InterviewReport existing = interviewReportMapper.selectOne(
                Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, sessionId));
        JobPosition job = jobPositionMapper.selectById(session.getJobId());
        Candidate candidate = candidateMapper.selectById(session.getCandidateId());
        List<InterviewMessage> messages = interviewMessageMapper.selectList(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getSequenceNo)
                        .orderByAsc(InterviewMessage::getId));
        messages = mergeDbAndBufferedMessages(messages, sessionId);
        messages = InterviewMessageUtils.mergeAdjacentSameRole(messages);

        if (tryCreateOpenAiEvaluationReport(sessionId, existing, job, candidate, messages)) {
            return;
        }

        List<InterviewMessage> candidateMessages = messages.stream()
                .filter(message -> "CANDIDATE".equals(message.getRole()))
                .filter(message -> StringUtils.isNotBlank(message.getContent()))
                .collect(Collectors.toList());
        String candidateAnswer = candidateMessages.stream()
                .map(InterviewMessage::getContent)
                .collect(Collectors.joining("\n"));
        int answerLength = candidateAnswer.length();
        int answerCount = candidateMessages.size();
        int keywordHits = countKeywordHits(candidateAnswer, job);
        int concreteEvidenceCount = countConcreteEvidence(candidateAnswer);
        int weakAnswerCount = countWeakAnswers(candidateMessages);

        BigDecimal answerQualityScore = scoreAnswerQuality(answerLength, answerCount, concreteEvidenceCount, weakAnswerCount);
        BigDecimal expressionScore = scoreExpression(answerLength, answerCount, weakAnswerCount);
        BigDecimal relevanceScore = scoreRelevance(keywordHits, answerQualityScore);
        BigDecimal experienceScore = scoreExperienceEvidence(concreteEvidenceCount, answerQualityScore, resumeText(candidate));
        BigDecimal communicationScore = scoreCommunication(answerCount, weakAnswerCount);
        List<EvaluationDimension> evaluationDimensions = listEvaluationDimensions(session.getJobId());
        if (evaluationDimensions.isEmpty()) {
            evaluationDimensions = genericRuleEvaluationDimensions(session.getJobId());
        }
        List<Map<String, Object>> dimensions = buildRuleDimensions(
                evaluationDimensions,
                answerQualityScore,
                expressionScore,
                relevanceScore,
                experienceScore,
                communicationScore
        );
        BigDecimal totalScore = weightedRuleTotalScore(dimensions, evaluationDimensions);

        String strengths = buildStrengths(totalScore, keywordHits, answerCount, answerLength, concreteEvidenceCount, weakAnswerCount, candidate);
        String risks = buildRisks(totalScore, keywordHits, answerCount, answerLength, concreteEvidenceCount, weakAnswerCount);
        String recommendation = recommendation(totalScore);
        List<String> followUpQuestions = buildFollowUpQuestions(job, keywordHits, answerCount);

        InterviewReport report = new InterviewReport();
        if (existing != null) {
            report.setId(existing.getId());
        }
        report.setSessionId(sessionId);
        report.setTotalScore(totalScore);
        report.setDimensionScoresJson(toJson(dimensions));
        report.setStrengths(strengths);
        report.setRisks(risks);
        report.setRecommendation(recommendation);
        report.setFollowUpQuestions(toJson(followUpQuestions));
        report.setRawReportJson(toJson(rawReport(totalScore, keywordHits, answerCount, answerLength, concreteEvidenceCount, weakAnswerCount, messages)));
        saveOrUpdateReport(existing, report);
    }

    private void markReportCompleted(Long sessionId) {
        InterviewSession session = getById(sessionId);
        if (session == null) {
            return;
        }
        session.setStatus(InterviewStatus.COMPLETED);
        session.setFailReason(null);
        updateById(session);
    }

    private void markReportFailed(Long sessionId, String reason) {
        InterviewSession session = getById(sessionId);
        if (session == null) {
            return;
        }
        session.setStatus(InterviewStatus.FAILED);
        session.setFailReason(StringUtils.isNotBlank(reason) ? reason : "面试报告生成失败");
        updateById(session);
    }

    private boolean tryCreateOpenAiEvaluationReport(
            Long sessionId,
            InterviewReport existing,
            JobPosition job,
            Candidate candidate,
            List<InterviewMessage> messages
    ) {
        return interviewReportAiComponent.generate(buildInterviewReportAiRequest(job, candidate, messages))
                .map(aiResult -> {
                    InterviewReport report = new InterviewReport();
                    if (existing != null) {
                        report.setId(existing.getId());
                    }
                    report.setSessionId(sessionId);
                    report.setTotalScore(aiResult.getTotalScore());
                    report.setDimensionScoresJson(aiResult.getDimensionScoresJson());
                    report.setStrengths(aiResult.getStrengths());
                    report.setRisks(aiResult.getRisks());
                    report.setRecommendation(aiResult.getRecommendation());
                    report.setFollowUpQuestions(aiResult.getFollowUpQuestions());
                    report.setRawReportJson(aiResult.getRawReportJson());
                    saveOrUpdateReport(existing, report);
                    return Boolean.TRUE;
                })
                .orElse(Boolean.FALSE);
    }

    private InterviewReportAiRequest buildInterviewReportAiRequest(
            JobPosition job,
            Candidate candidate,
            List<InterviewMessage> messages
    ) {
        InterviewReportAiRequest request = new InterviewReportAiRequest();
        if (job != null) {
            request.setJobTitle(job.getTitle());
            request.setJobDescription(job.getJd());
            request.setJobRequirements(job.getRequirements());
            request.setEvaluationDimensions(buildAiEvaluationDimensions(job.getId()));
        }
        if (candidate != null) {
            request.setCandidateName(candidate.getName());
            request.setResumeText(candidate.getResumeText());
        }
        List<InterviewReportAiRequest.InterviewReportMessage> aiMessages = new ArrayList<>();
        for (InterviewMessage message : messages == null ? List.<InterviewMessage>of() : messages) {
            InterviewReportAiRequest.InterviewReportMessage aiMessage = new InterviewReportAiRequest.InterviewReportMessage();
            aiMessage.setRole(message.getRole());
            aiMessage.setContent(message.getContent());
            aiMessages.add(aiMessage);
        }
        request.setMessages(aiMessages);
        return request;
    }

    private List<InterviewReportAiRequest.EvaluationDimension> buildAiEvaluationDimensions(Long jobId) {
        List<EvaluationDimension> dimensions = listEvaluationDimensions(jobId);
        List<InterviewReportAiRequest.EvaluationDimension> aiDimensions = new ArrayList<>();
        for (EvaluationDimension dimension : dimensions == null ? List.<EvaluationDimension>of() : dimensions) {
            if (StringUtils.isBlank(dimension.getName())) {
                continue;
            }
            InterviewReportAiRequest.EvaluationDimension aiDimension = new InterviewReportAiRequest.EvaluationDimension();
            aiDimension.setName(dimension.getName());
            aiDimension.setDescription(dimension.getDescription());
            aiDimension.setWeight(dimension.getWeight());
            aiDimensions.add(aiDimension);
        }
        return aiDimensions;
    }

    private List<EvaluationDimension> listEvaluationDimensions(Long jobId) {
        if (jobId == null) {
            return List.of();
        }
        List<EvaluationDimension> dimensions = evaluationDimensionMapper.selectList(
                Wrappers.lambdaQuery(EvaluationDimension.class)
                        .eq(EvaluationDimension::getJobId, jobId)
                        .orderByAsc(EvaluationDimension::getId));
        List<EvaluationDimension> validDimensions = new ArrayList<>();
        for (EvaluationDimension dimension : dimensions == null ? List.<EvaluationDimension>of() : dimensions) {
            if (StringUtils.isBlank(dimension.getName())) {
                continue;
            }
            validDimensions.add(dimension);
        }
        return validDimensions;
    }

    private List<EvaluationDimension> genericRuleEvaluationDimensions(Long jobId) {
        List<EvaluationDimension> dimensions = new ArrayList<>();
        dimensions.add(genericRuleEvaluationDimension(jobId, "回答有效性", "候选人是否正面回答问题，信息量、案例、细节和结论是否有效。", 30));
        dimensions.add(genericRuleEvaluationDimension(jobId, "表达完整度", "候选人表达是否连贯，是否能讲清背景、动作、结果和反思。", 20));
        dimensions.add(genericRuleEvaluationDimension(jobId, "岗位匹配度", "候选人本次回答与岗位 JD、能力要求、服务场景或业务场景的匹配程度。", 25));
        dimensions.add(genericRuleEvaluationDimension(jobId, "经验支撑度", "候选人是否用真实项目、客户、流程、工具、结果或数据证明经历。", 15));
        dimensions.add(genericRuleEvaluationDimension(jobId, "沟通配合度", "候选人互动意愿、理解问题、配合追问和沟通稳定性。", 10));
        return dimensions;
    }

    private EvaluationDimension genericRuleEvaluationDimension(Long jobId, String name, String description, int weight) {
        EvaluationDimension dimension = new EvaluationDimension();
        dimension.setJobId(jobId);
        dimension.setName(name);
        dimension.setDescription(description);
        dimension.setWeight(BigDecimal.valueOf(weight));
        return dimension;
    }

    private void saveOrUpdateReport(InterviewReport existing, InterviewReport report) {
        if (existing == null) {
            interviewReportMapper.insert(report);
        } else {
            interviewReportMapper.updateById(report);
        }
    }

    private boolean isPlaceholderReport(InterviewReport report) {
        return BigDecimal.ZERO.compareTo(report.getTotalScore()) == 0
                || "待接入 Realtime 模型后生成".equals(report.getStrengths());
    }

    private BigDecimal scoreByRange(int value, int low, int medium, int high) {
        if (value <= 0) {
            return BigDecimal.valueOf(20);
        }
        if (value < low) {
            return BigDecimal.valueOf(45);
        }
        if (value < medium) {
            return BigDecimal.valueOf(65);
        }
        if (value < high) {
            return BigDecimal.valueOf(80);
        }
        return BigDecimal.valueOf(90);
    }

    private BigDecimal scoreByKeywordHits(int hits) {
        if (hits <= 0) {
            return BigDecimal.valueOf(45);
        }
        if (hits <= 2) {
            return BigDecimal.valueOf(65);
        }
        if (hits <= 5) {
            return BigDecimal.valueOf(80);
        }
        return BigDecimal.valueOf(90);
    }

    private BigDecimal scoreAnswerQuality(int answerLength, int answerCount, int concreteEvidenceCount, int weakAnswerCount) {
        if (answerCount <= 0 || answerLength < 20) {
            return BigDecimal.valueOf(25);
        }
        BigDecimal base = scoreByRange(answerLength, 60, 180, 420);
        BigDecimal evidenceBonus = BigDecimal.valueOf(Math.min(concreteEvidenceCount * 6, 18));
        BigDecimal weakPenalty = BigDecimal.valueOf(Math.min(weakAnswerCount * 12, 40));
        return clampScore(base.add(evidenceBonus).subtract(weakPenalty));
    }

    private BigDecimal scoreExpression(int answerLength, int answerCount, int weakAnswerCount) {
        BigDecimal score = scoreByRange(answerLength, 50, 150, 360)
                .add(BigDecimal.valueOf(Math.min(answerCount * 2, 8)))
                .subtract(BigDecimal.valueOf(Math.min(weakAnswerCount * 10, 35)));
        return clampScore(score);
    }

    private BigDecimal scoreRelevance(int keywordHits, BigDecimal answerQualityScore) {
        BigDecimal keywordScore = scoreByKeywordHits(keywordHits);
        BigDecimal score = answerQualityScore.multiply(BigDecimal.valueOf(0.65))
                .add(keywordScore.multiply(BigDecimal.valueOf(0.35)));
        return clampScore(score);
    }

    private BigDecimal scoreExperienceEvidence(int concreteEvidenceCount, BigDecimal answerQualityScore, String resumeText) {
        BigDecimal evidenceScore;
        if (concreteEvidenceCount <= 0) {
            evidenceScore = BigDecimal.valueOf(35);
        } else if (concreteEvidenceCount == 1) {
            evidenceScore = BigDecimal.valueOf(60);
        } else if (concreteEvidenceCount <= 3) {
            evidenceScore = BigDecimal.valueOf(75);
        } else {
            evidenceScore = BigDecimal.valueOf(88);
        }
        BigDecimal resumeBonus = StringUtils.isNotBlank(stripHtml(resumeText)) ? BigDecimal.valueOf(5) : BigDecimal.ZERO;
        BigDecimal score = answerQualityScore.multiply(BigDecimal.valueOf(0.55))
                .add(evidenceScore.multiply(BigDecimal.valueOf(0.45)))
                .add(resumeBonus);
        return clampScore(score);
    }

    private BigDecimal scoreCommunication(int answerCount, int weakAnswerCount) {
        BigDecimal score = scoreByRange(answerCount, 1, 3, 6)
                .subtract(BigDecimal.valueOf(Math.min(weakAnswerCount * 8, 32)));
        return clampScore(score);
    }

    private BigDecimal clampScore(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(20)) < 0) {
            return BigDecimal.valueOf(20);
        }
        if (score.compareTo(BigDecimal.valueOf(95)) > 0) {
            return BigDecimal.valueOf(95);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private int countKeywordHits(String candidateAnswer, JobPosition job) {
        String text = stripHtml(candidateAnswer).toLowerCase();
        String source = stripHtml(
                nullToEmpty(job == null ? null : job.getTitle()) + "\n"
                        + nullToEmpty(job == null ? null : job.getJd()) + "\n"
                        + nullToEmpty(job == null ? null : job.getRequirements())
        );
        int hits = 0;
        for (String keyword : source.split("[，。；、,.;\\s]+")) {
            String normalized = keyword.trim().toLowerCase();
            if (normalized.length() >= 2 && text.contains(normalized)) {
                hits++;
            }
            if (hits >= 8) {
                return hits;
            }
        }
        return hits;
    }

    private int countConcreteEvidence(String candidateAnswer) {
        String text = stripHtml(candidateAnswer).toLowerCase();
        int count = 0;
        String[] keywords = {
                "项目", "负责", "实现", "设计", "架构", "优化", "排查", "解决", "上线", "性能",
                "接口", "数据库", "缓存", "并发", "部署", "组件", "模块", "难点", "结果", "提升",
                "spring", "vue", "mysql", "redis", "docker", "k8s", "kubernetes", "mq", "elasticsearch",
                "客户", "需求", "行程", "预算", "酒店", "航班", "地接", "供应商", "成交", "转化",
                "投诉", "应急", "服务", "沟通", "协调", "方案", "定制", "资源", "培训", "话术"
        };
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return Math.min(count, 10);
    }

    private int countWeakAnswers(List<InterviewMessage> candidateMessages) {
        int count = 0;
        for (InterviewMessage message : candidateMessages) {
            String content = stripHtml(message.getContent()).trim();
            if (content.length() <= 6 || WEAK_ANSWER_PATTERN.matcher(content).matches()) {
                count++;
            }
        }
        return count;
    }

    private List<Map<String, Object>> buildRuleDimensions(
            List<EvaluationDimension> evaluationDimensions,
            BigDecimal answerQualityScore,
            BigDecimal expressionScore,
            BigDecimal relevanceScore,
            BigDecimal experienceScore,
            BigDecimal communicationScore
    ) {
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (EvaluationDimension evaluationDimension : evaluationDimensions == null ? List.<EvaluationDimension>of() : evaluationDimensions) {
            BigDecimal score = ruleScoreForDimension(
                    evaluationDimension.getName(),
                    answerQualityScore,
                    expressionScore,
                    relevanceScore,
                    experienceScore,
                    communicationScore
            );
            dimensions.add(dimension(evaluationDimension.getName(), score, ruleCommentForDimension(evaluationDimension, score)));
        }
        return dimensions;
    }

    private BigDecimal ruleScoreForDimension(
            String name,
            BigDecimal answerQualityScore,
            BigDecimal expressionScore,
            BigDecimal relevanceScore,
            BigDecimal experienceScore,
            BigDecimal communicationScore
    ) {
        String dimensionName = nullToEmpty(name);
        if (dimensionName.contains("回答") || dimensionName.contains("有效")) {
            return answerQualityScore;
        }
        if (dimensionName.contains("表达") || dimensionName.contains("完整")) {
            return expressionScore;
        }
        if (dimensionName.contains("岗位") || dimensionName.contains("匹配") || dimensionName.contains("专业") || dimensionName.contains("核心")
                || dimensionName.contains("行程") || dimensionName.contains("方案") || dimensionName.contains("目的地")) {
            return relevanceScore;
        }
        if (dimensionName.contains("经验") || dimensionName.contains("案例") || dimensionName.contains("证据") || dimensionName.contains("支撑")
                || dimensionName.contains("资源") || dimensionName.contains("应急") || dimensionName.contains("预算")) {
            return experienceScore;
        }
        if (dimensionName.contains("沟通") || dimensionName.contains("配合") || dimensionName.contains("服务") || dimensionName.contains("客户")
                || dimensionName.contains("需求") || dimensionName.contains("销售") || dimensionName.contains("转化")) {
            return communicationScore;
        }
        return answerQualityScore.multiply(BigDecimal.valueOf(0.35))
                .add(relevanceScore.multiply(BigDecimal.valueOf(0.25)))
                .add(expressionScore.multiply(BigDecimal.valueOf(0.15)))
                .add(experienceScore.multiply(BigDecimal.valueOf(0.15)))
                .add(communicationScore.multiply(BigDecimal.valueOf(0.10)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String ruleCommentForDimension(EvaluationDimension evaluationDimension, BigDecimal score) {
        String description = evaluationDimension == null ? "" : evaluationDimension.getDescription();
        if (StringUtils.isNotBlank(description)) {
            return "AI 报告临时不可用，系统按该维度规则兜底评分：" + description;
        }
        return "AI 报告临时不可用，系统根据候选人回答内容、岗位匹配和有效证据兜底评分。";
    }

    private BigDecimal weightedRuleTotalScore(List<Map<String, Object>> dimensions, List<EvaluationDimension> evaluationDimensions) {
        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (int i = 0; i < dimensions.size(); i++) {
            BigDecimal score = mapScore(dimensions.get(i));
            BigDecimal weight = normalizeWeight(i < evaluationDimensions.size() ? evaluationDimensions.get(i).getWeight() : null);
            weightedScore = weightedScore.add(score.multiply(weight));
            weightSum = weightSum.add(weight);
        }
        if (weightSum.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return clampScore(weightedScore.divide(weightSum, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal mapScore(Map<String, Object> dimension) {
        Object value = dimension == null ? null : dimension.get("score");
        if (value instanceof BigDecimal score) {
            return clampScore(score);
        }
        try {
            return clampScore(new BigDecimal(String.valueOf(value)));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return weight.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> dimension(String name, BigDecimal score, String comment) {
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("name", name);
        dimension.put("score", score);
        dimension.put("comment", comment);
        return dimension;
    }

    private String buildStrengths(BigDecimal score, int keywordHits, int answerCount, int answerLength, int concreteEvidenceCount, int weakAnswerCount, Candidate candidate) {
        List<String> strengths = new ArrayList<>();
        if (answerCount >= 3 && weakAnswerCount < answerCount) {
            strengths.add("候选人能持续完成多轮面试互动，沟通配合度较好。");
        }
        if (answerLength >= 120) {
            strengths.add("候选人回答包含一定信息量，具备进一步评估基础。");
        }
        if (keywordHits >= 3 && concreteEvidenceCount >= 2) {
            strengths.add("回答中体现出与岗位要求相关的工作经验或项目案例。");
        }
        if (concreteEvidenceCount >= 3) {
            strengths.add("候选人能提供一定案例、方法、工具或问题解决证据。");
        }
        if (StringUtils.isNotBlank(resumeText(candidate))) {
            strengths.add("候选人已提供简历材料，可结合面试记录继续复核项目经历。");
        }
        if (strengths.isEmpty()) {
            strengths.add("本次面试记录较少，暂未形成明显优势结论。");
        }
        return String.join("\n", strengths);
    }

    private String buildRisks(BigDecimal score, int keywordHits, int answerCount, int answerLength, int concreteEvidenceCount, int weakAnswerCount) {
        List<String> risks = new ArrayList<>();
        if (answerCount < 2) {
            risks.add("有效问答轮次偏少，评分可信度有限。");
        }
        if (weakAnswerCount > 0) {
            risks.add("候选人存在较多短答、泛答或无效回答，实际能力证据不足。");
        }
        if (answerLength < 80) {
            risks.add("候选人回答偏短，项目细节和能力证据不足。");
        }
        if (keywordHits < 2) {
            risks.add("回答与岗位 JD、能力要求的直接匹配信息偏少。");
        }
        if (concreteEvidenceCount < 2) {
            risks.add("回答中缺少具体案例、业务细节、问题处理过程或结果数据。");
        }
        if (score.compareTo(BigDecimal.valueOf(60)) < 0) {
            risks.add("综合表现暂未达到推荐阈值，建议 HR 进一步人工复核。");
        }
        if (risks.isEmpty()) {
            risks.add("暂未发现明显风险，建议结合简历真实性和后续技术追问继续确认。");
        }
        return String.join("\n", risks);
    }

    private String recommendation(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "RECOMMEND";
        }
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "HOLD";
        }
        return "REJECT";
    }

    private List<String> buildFollowUpQuestions(JobPosition job, int keywordHits, int answerCount) {
        List<String> questions = new ArrayList<>();
        questions.add("请结合一个真实工作案例，说明你负责的内容、遇到的难点、处理过程和最终结果。");
        if (job != null && StringUtils.isNotBlank(job.getRequirements())) {
            questions.add("岗位能力要求中你最有把握的一项是什么？请给出具体案例。");
        }
        if (keywordHits < 3) {
            questions.add("请进一步说明你的经历与该岗位 JD 的匹配点。");
        }
        if (answerCount < 3) {
            questions.add("请补充一次完整的自我介绍，并突出最近一段核心工作经历。");
        }
        return questions;
    }

    private Map<String, Object> rawReport(BigDecimal score, int keywordHits, int answerCount, int answerLength, int concreteEvidenceCount, int weakAnswerCount, List<InterviewMessage> messages) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("score", score);
        raw.put("keywordHits", keywordHits);
        raw.put("answerCount", answerCount);
        raw.put("answerLength", answerLength);
        raw.put("concreteEvidenceCount", concreteEvidenceCount);
        raw.put("weakAnswerCount", weakAnswerCount);
        raw.put("messageCount", messages.size());
        raw.put("generatedBy", "rule-based-interview-report-v2");
        return raw;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String resumeText(Candidate candidate) {
        return candidate == null ? "" : nullToEmpty(candidate.getResumeText());
    }

    private String stripHtml(String value) {
        return nullToEmpty(value).replaceAll("<[^>]+>", " ");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private InterviewReportResponse toReportResponse(InterviewReport report) {
        InterviewReportResponse response = new InterviewReportResponse();
        response.setId(report.getId());
        response.setSessionId(report.getSessionId());
        response.setTotalScore(report.getTotalScore());
        response.setDimensionScoresJson(report.getDimensionScoresJson());
        response.setStrengths(report.getStrengths());
        response.setRisks(report.getRisks());
        response.setRecommendation(report.getRecommendation());
        response.setFollowUpQuestions(report.getFollowUpQuestions());
        response.setCreatedAt(report.getCreatedAt());
        return response;
    }

    private InterviewReportListItemResponse toReportListItem(
            InterviewReport report,
            InterviewSession session,
            JobPosition job,
            Candidate candidate
    ) {
        InterviewReportListItemResponse response = new InterviewReportListItemResponse();
        response.setReportId(report.getId());
        response.setSessionId(report.getSessionId());
        if (session != null) {
            response.setJobId(session.getJobId());
            response.setCandidateId(session.getCandidateId());
            response.setStartedAt(session.getStartedAt());
            response.setEndedAt(session.getEndedAt());
        }
        if (job != null) {
            response.setJobTitle(job.getTitle());
        }
        if (candidate != null) {
            response.setCandidateName(candidate.getName());
        }
        response.setTotalScore(report.getTotalScore());
        response.setRecommendation(report.getRecommendation());
        response.setReportCreatedAt(report.getCreatedAt());
        return response;
    }

}
