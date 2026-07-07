package com.zook.hrinterview.realtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.interfaces.interview.InterviewStatus;
import com.zook.hrinterview.interfaces.interview.entity.InterviewReport;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewReportMapper;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.interfaces.interview.service.impl.InterviewServiceImpl;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import com.zook.hrinterview.realtime.event.InterviewAutoFinishEvent;
import com.zook.hrinterview.utils.RedisUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class LocalInterviewAutoFinishSmokeTest {

    @Resource
    private InterviewServiceImpl interviewService;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private InterviewReportMapper interviewReportMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private RedisUtils redisUtils;

    private final List<Long> sessionIds = new ArrayList<>();

    private Long candidateId;

    private Long jobId;

    @AfterEach
    void cleanup() {
        for (Long sessionId : sessionIds) {
            interviewReportMapper.delete(Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, sessionId));
            interviewSessionMapper.deleteById(sessionId);
            redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_QUEUE_FLAG, sessionId);
            redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_RETRY_COUNT, sessionId);
            redisUtils.lRemove(RedisKeyEnum.INTERVIEW_REPORT_PENDING_QUEUE, 0, sessionId);
            redisUtils.lRemove(RedisKeyEnum.INTERVIEW_REPORT_PROCESSING_QUEUE, 0, sessionId);
        }
        if (candidateId != null) {
            candidateMapper.deleteById(candidateId);
        }
        if (jobId != null) {
            jobPositionMapper.deleteById(jobId);
        }
    }

    @Test
    void shouldAutoFinishOnlyWhenInterviewIsInProgress() {
        Assumptions.assumeTrue(Boolean.getBoolean("local.auto-finish.smoke"),
                "Set -Dlocal.auto-finish.smoke=true to run this local smoke test");

        createJobAndCandidate();
        Long inProgressSessionId = createSession(InterviewStatus.IN_PROGRESS);
        Long waitingSessionId = createSession(InterviewStatus.WAITING);

        interviewService.handleInterviewAutoFinish(new InterviewAutoFinishEvent(inProgressSessionId, "smoke_question_limit"));
        interviewService.handleInterviewAutoFinish(new InterviewAutoFinishEvent(waitingSessionId, "smoke_question_limit"));
        interviewService.handleInterviewAutoFinish(new InterviewAutoFinishEvent(inProgressSessionId, "duplicate_smoke_question_limit"));

        InterviewSession finished = interviewSessionMapper.selectById(inProgressSessionId);
        assertEquals(InterviewStatus.GENERATING, finished.getStatus());
        assertNotNull(finished.getEndedAt());

        InterviewSession untouched = interviewSessionMapper.selectById(waitingSessionId);
        assertEquals(InterviewStatus.WAITING, untouched.getStatus());
        assertNull(untouched.getEndedAt());
    }

    private void createJobAndCandidate() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        JobPosition job = new JobPosition();
        job.setTitle("Codex自动结束边界测试-" + suffix);
        job.setJd("用于本地自动结束边界测试");
        job.setRequirements("只测试状态流，不触发真实语音");
        job.setStatus("ENABLED");
        job.setCreatedBy(1L);
        jobPositionMapper.insert(job);
        jobId = job.getId();

        Candidate candidate = new Candidate();
        candidate.setJobId(jobId);
        candidate.setName("Codex自动结束候选人-" + suffix);
        candidate.setGender("UNKNOWN");
        candidate.setAge(28);
        candidate.setPhone("13800000000");
        candidate.setEmail("codex-auto-finish@example.com");
        candidate.setResumeText("用于本地自动结束边界测试");
        candidate.setCreatedBy(1L);
        candidateMapper.insert(candidate);
        candidateId = candidate.getId();
    }

    private Long createSession(String status) {
        InterviewSession session = new InterviewSession();
        session.setJobId(jobId);
        session.setCandidateId(candidateId);
        session.setStatus(status);
        session.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        session.setAccessCodeHash("local-smoke");
        if (InterviewStatus.IN_PROGRESS.equals(status)) {
            session.setStartedAt(LocalDateTime.now());
        }
        interviewSessionMapper.insert(session);
        sessionIds.add(session.getId());
        return session.getId();
    }
}
