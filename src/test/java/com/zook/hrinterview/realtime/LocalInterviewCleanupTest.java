package com.zook.hrinterview.realtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
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
import com.zook.hrinterview.utils.RedisUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class LocalInterviewCleanupTest {

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private InterviewReportMapper interviewReportMapper;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private EvaluationDimensionMapper evaluationDimensionMapper;

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private RedisUtils redisUtils;

    @Test
    void cleanupLocalSmokeData() {
        Long sessionId = Long.getLong("local.cleanup.session-id");
        Long candidateId = Long.getLong("local.cleanup.candidate-id");
        Long jobId = Long.getLong("local.cleanup.job-id");
        Assumptions.assumeTrue(sessionId != null && candidateId != null && jobId != null,
                "Set local cleanup ids to run this cleanup test");

        interviewReportMapper.delete(Wrappers.lambdaQuery(InterviewReport.class).eq(InterviewReport::getSessionId, sessionId));
        interviewMessageMapper.delete(Wrappers.lambdaQuery(InterviewMessage.class).eq(InterviewMessage::getSessionId, sessionId));
        interviewSessionMapper.delete(Wrappers.lambdaQuery(InterviewSession.class).eq(InterviewSession::getId, sessionId));
        candidateMapper.delete(Wrappers.lambdaQuery(Candidate.class).eq(Candidate::getId, candidateId));
        evaluationDimensionMapper.delete(Wrappers.lambdaQuery(EvaluationDimension.class).eq(EvaluationDimension::getJobId, jobId));
        jobPositionMapper.delete(Wrappers.lambdaQuery(JobPosition.class).eq(JobPosition::getId, jobId));

        redisUtils.delete(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, sessionId);
        redisUtils.delete(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER, sessionId);
        redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_QUEUE_FLAG, sessionId);
        redisUtils.delete(RedisKeyEnum.INTERVIEW_REPORT_RETRY_COUNT, sessionId);
    }
}
