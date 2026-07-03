package com.zook.hrinterview.interfaces.interview.service.impl;

import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
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
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class InterviewServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSession> implements InterviewService {

    private static final String STATUS_ENABLED = "ENABLED";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Pattern WEAK_ANSWER_PATTERN = Pattern.compile(
            "^(嗯+|啊+|哦+|好+|有+|没有+|不会+|不知道+|不清楚+|随便|都行|什么都会|no+|hello|hi|你好)[。.!！,，\\s]*$",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private JobPositionMapper jobPositionMapper;

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
        appendMessage(session.getId(), "AI", "你好，我是本次 AI 面试官。请先用一分钟介绍一下你自己。");
        return toDetailResponse(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewDetailResponse finish(IdRequest request) {
        InterviewSession session = mustGetSession(request.getId());
        if (!InterviewStatus.IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_STATUS_INVALID);
        }
        session.setStatus(InterviewStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        updateById(session);
        createEvaluationReport(session);
        return toDetailResponse(session);
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
                        .orderByAsc(InterviewMessage::getSequenceNo));
        return messages.stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    @Override
    public InterviewReportResponse report(IdRequest request) {
        InterviewReport report = interviewReportMapper.selectOne(
                Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, request.getId()));
        if (report == null || isPlaceholderReport(report)) {
            InterviewSession session = mustGetSession(request.getId());
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
        LambdaQueryWrapper<InterviewReport> wrapper = Wrappers.lambdaQuery(InterviewReport.class)
                .eq(StringUtils.isNotBlank(request.getRecommendation()), InterviewReport::getRecommendation, request.getRecommendation())
                .orderByDesc(InterviewReport::getCreatedAt);
        List<InterviewReport> allReports = interviewReportMapper.selectList(wrapper);
        Map<Long, InterviewSession> sessionMap = batchQuerySessionsByReport(allReports);
        Map<Long, JobPosition> jobMap = batchQueryJobs(new ArrayList<>(sessionMap.values()));
        Map<Long, Candidate> candidateMap = batchQueryCandidates(new ArrayList<>(sessionMap.values()));
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim().toLowerCase();
        List<InterviewReportListItemResponse> matched = allReports.stream()
                .map(report -> toReportListItem(
                        report,
                        sessionMap.get(report.getSessionId()),
                        sessionMap.get(report.getSessionId()) == null ? null : jobMap.get(sessionMap.get(report.getSessionId()).getJobId()),
                        sessionMap.get(report.getSessionId()) == null ? null : candidateMap.get(sessionMap.get(report.getSessionId()).getCandidateId())
                ))
                .filter(item -> matchesReportKeyword(item, keyword))
                .collect(Collectors.toList());
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
        int fromIndex = Math.min((pageNo - 1) * pageSize, matched.size());
        int toIndex = Math.min(fromIndex + pageSize, matched.size());
        return new PageResponse<>(
                matched.subList(fromIndex, toIndex),
                (long) matched.size(),
                (long) pageNo,
                (long) pageSize
        );
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
        Long count = interviewMessageMapper.selectCount(
                Wrappers.lambdaQuery(InterviewMessage.class).eq(InterviewMessage::getSessionId, sessionId));
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNo(count.intValue() + 1);
        interviewMessageMapper.insert(message);
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

    private void createEvaluationReport(InterviewSession session) {
        Long sessionId = session.getId();
        InterviewReport existing = interviewReportMapper.selectOne(
                Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, sessionId));
        JobPosition job = jobPositionMapper.selectById(session.getJobId());
        Candidate candidate = candidateMapper.selectById(session.getCandidateId());
        List<InterviewMessage> messages = interviewMessageMapper.selectList(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getSequenceNo));

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
        BigDecimal totalScore = answerQualityScore
                .multiply(BigDecimal.valueOf(0.40))
                .add(expressionScore.multiply(BigDecimal.valueOf(0.20)))
                .add(relevanceScore.multiply(BigDecimal.valueOf(0.15)))
                .add(experienceScore.multiply(BigDecimal.valueOf(0.15)))
                .add(communicationScore.multiply(BigDecimal.valueOf(0.10)))
                .setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> dimensions = new ArrayList<>();
        dimensions.add(dimension("回答有效性", answerQualityScore, "根据候选人实际回答的信息量、具体案例、有效表达和无效回答比例评估"));
        dimensions.add(dimension("表达完整度", expressionScore, "根据候选人回答长度、完整性和有效信息量评估"));
        dimensions.add(dimension("岗位匹配度", relevanceScore, "主要根据实际回答与岗位 JD、能力要求的匹配情况评估，简历仅作辅助"));
        dimensions.add(dimension("经验支撑度", experienceScore, "根据候选人在回答中提供的项目、技术、问题解决等具体证据评估"));
        dimensions.add(dimension("沟通配合度", communicationScore, "根据互动轮次、回答意愿和沟通连贯性评估"));

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
                "spring", "vue", "mysql", "redis", "docker", "k8s", "kubernetes", "mq", "elasticsearch"
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
            strengths.add("回答中体现出与岗位要求相关的技术或项目信息。");
        }
        if (concreteEvidenceCount >= 3) {
            strengths.add("候选人能提供一定项目、技术或问题解决证据。");
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
            risks.add("回答中缺少具体项目案例、技术细节、问题排查或结果数据。");
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
        questions.add("请结合一个真实项目，说明你在其中负责的模块、技术难点和最终结果。");
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

    private boolean matchesReportKeyword(InterviewReportListItemResponse item, String keyword) {
        if (!StringUtils.isNotBlank(keyword)) {
            return true;
        }
        return nullToEmpty(item.getCandidateName()).toLowerCase().contains(keyword)
                || nullToEmpty(item.getJobTitle()).toLowerCase().contains(keyword);
    }
}
