package com.zook.hrinterview.realtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageListRequest;
import com.zook.hrinterview.interfaces.interview.dto.InterviewMessageResponse;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.interview.service.InterviewService;
import com.zook.hrinterview.realtime.service.RealtimeMessagePersistService;
import com.zook.hrinterview.utils.RedisUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LocalInterviewPersistenceSmokeTest {

    @Resource
    private RealtimeMessagePersistService realtimeMessagePersistService;

    @Resource
    private InterviewService interviewService;

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private RedisUtils redisUtils;

    @Test
    void shouldReadBufferedMessagesBeforeDatabaseFlushAndPersistEventually() throws Exception {
        Long sessionId = Long.getLong("local.smoke.session-id");
        Assumptions.assumeTrue(sessionId != null, "Set -Dlocal.smoke.session-id=xxx to run this local smoke test");

        int baseSequenceNo = maxSequenceNo(sessionId);
        enqueue(sessionId, "AI", baseSequenceNo + 1, "你好，我是本次 AI 面试官。本次是 Codex 本地队列测试。");
        enqueue(sessionId, "CANDIDATE", baseSequenceNo + 2, "我有旅游顾问经验，擅长客户需求沟通。");

        InterviewMessage candidateUpdate = message(sessionId, "CANDIDATE", baseSequenceNo + 2,
                "我有旅游顾问经验，擅长客户需求沟通，也处理过航班取消和酒店协调。");
        realtimeMessagePersistService.updateAsync(candidateUpdate).join();

        List<InterviewMessageResponse> immediateMessages = listMessages(sessionId);
        assertContains(immediateMessages, "Codex 本地队列测试");
        assertContains(immediateMessages, "酒店协调");

        Thread.sleep(4500);

        List<InterviewMessageResponse> persistedMessages = listMessages(sessionId);
        assertContains(persistedMessages, "Codex 本地队列测试");
        assertContains(persistedMessages, "酒店协调");

        Long dbCount = interviewMessageMapper.selectCount(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .ge(InterviewMessage::getSequenceNo, baseSequenceNo + 1)
                        .le(InterviewMessage::getSequenceNo, baseSequenceNo + 2));
        assertTrue(dbCount >= 2, "messages should be flushed to database");

        Object bufferedCandidate = redisUtils.hGet(
                RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER.buildKey(sessionId),
                String.valueOf(baseSequenceNo + 2));
        assertTrue(bufferedCandidate == null, "flushed message buffer should be cleaned");
    }

    private void enqueue(Long sessionId, String role, int sequenceNo, String content) {
        realtimeMessagePersistService.insertAsync(message(sessionId, role, sequenceNo, content)).join();
    }

    private InterviewMessage message(Long sessionId, String role, int sequenceNo, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        return message;
    }

    private List<InterviewMessageResponse> listMessages(Long sessionId) {
        IdRequest detail = new IdRequest();
        detail.setId(sessionId);
        interviewService.detail(detail);
        InterviewMessageListRequest request = new InterviewMessageListRequest();
        request.setSessionId(sessionId);
        return interviewService.listMessages(request);
    }

    private int maxSequenceNo(Long sessionId) {
        InterviewMessage message = interviewMessageMapper.selectOne(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .select(InterviewMessage::getSequenceNo)
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByDesc(InterviewMessage::getSequenceNo)
                        .last("limit 1"));
        return message == null || message.getSequenceNo() == null ? 0 : message.getSequenceNo();
    }

    private void assertContains(List<InterviewMessageResponse> messages, String content) {
        assertTrue(messages.stream().anyMatch(message -> message.getContent() != null && message.getContent().contains(content)),
                "messages should contain: " + content);
    }
}
