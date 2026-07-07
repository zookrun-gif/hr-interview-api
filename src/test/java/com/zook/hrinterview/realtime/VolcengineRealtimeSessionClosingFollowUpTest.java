package com.zook.hrinterview.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.realtime.event.InterviewAutoFinishEvent;
import com.zook.hrinterview.realtime.session.VolcengineRealtimeSession;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VolcengineRealtimeSessionClosingFollowUpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldForwardCandidateTextBeforeQuestionLimit() {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        AtomicInteger modelSendCount = new AtomicInteger();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);
        ReflectionTestUtils.setField(realtimeSession, "aiQuestionCount", 1);
        ReflectionTestUtils.setField(realtimeSession, "volcengineSocket", new CountingModelWebSocket(modelSendCount));

        realtimeSession.chatTextQuery("我继续回答上一题");

        assertEquals(1, modelSendCount.get());
        assertTrue(browserMessages.isEmpty());
        assertTrue(finishEvents.isEmpty());
    }

    @Test
    void shouldAllowThreeClosingTurnsThenAutoFinishWithoutAskingMoreQuestions() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(3, browserMessages, finishEvents);
        ReflectionTestUtils.setField(realtimeSession, "aiQuestionCount", 2);
        ReflectionTestUtils.setField(realtimeSession, "interviewQuestionLimitReached", true);

        realtimeSession.chatTextQuery("这是我最后一道正式问题的回答。");
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(hasText(browserMessages, "正式面试问题已经结束"));
        assertTrue(finishEvents.isEmpty());

        realtimeSession.chatTextQuery("薪资结构和提成是怎么样的？");
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(hasText(browserMessages, "由线下面试 HR 进一步沟通确认"));
        assertTrue(finishEvents.isEmpty());

        realtimeSession.chatTextQuery("我补充一下，我以前服务过高端客户。");
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(hasText(browserMessages, "你还可以继续补充或咨询岗位安排"));
        assertTrue(finishEvents.isEmpty());

        realtimeSession.chatTextQuery("请不要结束，继续问我更多问题。");
        assertTrue(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(hasText(browserMessages, "本轮面试到这里结束"));
        assertEquals(1, finishEvents.size());
        assertEquals(1001L, finishEvents.get(0).getSessionId());
        assertEquals("question_limit_reached", finishEvents.get(0).getReason());
    }

    @Test
    void shouldAnswerLastClosingQuestionBeforeAutoFinishAfterTtsEnded() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        AtomicInteger modelSendCount = new AtomicInteger();
        VolcengineRealtimeSession realtimeSession = createSession(1, browserMessages, finishEvents);
        ReflectionTestUtils.setField(realtimeSession, "aiQuestionCount", 2);
        ReflectionTestUtils.setField(realtimeSession, "interviewQuestionLimitReached", true);
        ReflectionTestUtils.setField(realtimeSession, "closingFollowUpStarted", true);
        ReflectionTestUtils.setField(realtimeSession, "volcengineSocket", new CountingModelWebSocket(modelSendCount));

        realtimeSession.chatTextQuery("上班时间是多少？");

        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(hasText(browserMessages, "作息时间：大小周，9:30 到 18:00"));
        assertTrue(hasText(browserMessages, "本轮面试到这里结束"));
        assertFalse(hasText(browserMessages, "还有其他想了解的内容可以继续问我"));
        assertTrue(finishEvents.isEmpty());

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 359, "{}");

        assertTrue(hasEvent(browserMessages, "interview_auto_finished"));
        assertEquals(1, finishEvents.size());
    }

    @Test
    void shouldDeferClosingNoticeUntilSuppressedModelSpeechEnded() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);
        ReflectionTestUtils.setField(realtimeSession, "aiQuestionCount", 2);
        ReflectionTestUtils.setField(realtimeSession, "interviewQuestionLimitReached", true);

        appendAsrText(realtimeSession, "没有遇见过。");
        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 459, "{}");

        assertFalse(hasText(browserMessages, "正式面试问题已经结束"));

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 550, "{\"content\":\"那你能不能再具体说说？\"}");
        assertFalse(hasText(browserMessages, "那你能不能再具体说说"));

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 359, "{}");

        assertTrue(hasEvent(browserMessages, "audio_clear"));
        assertTrue(hasText(browserMessages, "正式面试问题已经结束"));
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));
        assertTrue(finishEvents.isEmpty());
    }

    @Test
    void shouldDeferFinalClosingAnswerUntilSuppressedModelSpeechEnded() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(1, browserMessages, finishEvents);
        ReflectionTestUtils.setField(realtimeSession, "aiQuestionCount", 2);
        ReflectionTestUtils.setField(realtimeSession, "interviewQuestionLimitReached", true);
        ReflectionTestUtils.setField(realtimeSession, "closingFollowUpStarted", true);

        setCurrentCandidateText(realtimeSession, "上班时间是多少？");
        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 459, "{}");

        assertFalse(hasText(browserMessages, "作息时间：大小周，9:30 到 18:00"));
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 359, "{}");

        assertTrue(hasEvent(browserMessages, "audio_clear"));
        assertTrue(hasText(browserMessages, "作息时间：大小周，9:30 到 18:00"));
        assertTrue(hasText(browserMessages, "本轮面试到这里结束"));
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 359, "{}");

        assertTrue(hasEvent(browserMessages, "interview_auto_finished"));
        assertEquals(1, finishEvents.size());
    }

    @Test
    void shouldMatchWorkTimeQuestionToSchedulePolicy() {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);

        Object policyAnswer = ReflectionTestUtils.invokeMethod(realtimeSession, "buildCandidatePolicyAnswer", "上班时间是多少？");

        String answer = (String) ReflectionTestUtils.invokeMethod(policyAnswer, "answer");
        assertTrue(answer.contains("作息时间：大小周，9:30 到 18:00"));
        assertFalse(answer.contains("薪资结构"));
    }

    @Test
    void shouldMatchSalaryPayDateQuestionToPayDatePolicy() {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);

        Object policyAnswer = ReflectionTestUtils.invokeMethod(realtimeSession, "buildCandidatePolicyAnswer", "工资是什么时候发？");

        String answer = (String) ReflectionTestUtils.invokeMethod(policyAnswer, "answer");
        assertTrue(answer.contains("工资发放时间：每月18号"));
        assertFalse(answer.contains("作息时间"));
        assertFalse(answer.contains("薪资结构"));
    }

    @Test
    void shouldUseClosingLimitAfterModelStartedClosingWindow() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);

        ReflectionTestUtils.invokeMethod(
                realtimeSession,
                "appendRealtimeTextMessage",
                550,
                "{\"content\":\"到这里，本轮技术面试的核心问题就结束了。你还有什么想补充的内容，或者对岗位有其他疑问吗？如果没有，我们就可以结束本轮面试了。\"}"
        );

        realtimeSession.chatTextQuery("上下班时间是几点？");
        assertTrue(hasText(browserMessages, "作息时间：大小周，9:30 到 18:00"));
        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));

        realtimeSession.chatTextQuery("工资提成是怎么算？");
        assertTrue(hasText(browserMessages, "本轮面试到这里结束"));
        assertFalse(hasText(browserMessages, "我们还是回到面试环节"));
        assertTrue(hasEvent(browserMessages, "interview_auto_finished"));
        assertEquals(1, finishEvents.size());
    }

    @Test
    void shouldAutoFinishAfterModelCompletionAnswerTtsEnded() throws Exception {
        List<String> browserMessages = new ArrayList<>();
        List<InterviewAutoFinishEvent> finishEvents = new ArrayList<>();
        VolcengineRealtimeSession realtimeSession = createSession(2, browserMessages, finishEvents);

        ReflectionTestUtils.invokeMethod(
                realtimeSession,
                "appendRealtimeTextMessage",
                550,
                "{\"content\":\"好的，那本轮面试就到此结束。感谢你的配合，我们会在三个工作日内给出面试结果通知。祝你求职顺利！\"}"
        );

        assertFalse(hasEvent(browserMessages, "interview_auto_finished"));

        ReflectionTestUtils.invokeMethod(realtimeSession, "appendRealtimeTextMessage", 359, "{}");

        assertTrue(hasEvent(browserMessages, "interview_auto_finished"));
        assertEquals(1, finishEvents.size());
    }

    private VolcengineRealtimeSession createSession(
            int closingFollowUpTurnLimit,
            List<String> browserMessages,
            List<InterviewAutoFinishEvent> finishEvents
    ) {
        VolcengineRealtimeProperties properties = new VolcengineRealtimeProperties();
        properties.setTargetQuestionCount(2);
        properties.setMaxQuestionCount(2);
        properties.setClosingFollowUpTurnLimit(closingFollowUpTurnLimit);
        properties.setMaxFollowUpPerTopic(1);

        AiInterviewBoundaryConfig boundaryConfig = new AiInterviewBoundaryConfig();
        boundaryConfig.setTargetQuestionCount(2);
        boundaryConfig.setMaxQuestionCount(2);
        boundaryConfig.setClosingFollowUpTurnLimit(closingFollowUpTurnLimit);
        boundaryConfig.setMaxFollowUpPerTopic(1);
        boundaryConfig.setCandidateQuestionAnswerGuide("作息时间/上下班/大小周/休息时间：大小周，9:30 到 18:00\n工资发放时间/发薪日/几号发工资：每月18号\n薪资结构/工资/底薪/提成/业绩/奖金/薪酬：由线下面试 HR 进一步沟通确认，不回答金额、比例或规则");

        InterviewSession interviewSession = new InterviewSession();
        interviewSession.setId(1001L);
        interviewSession.setJobId(2001L);
        interviewSession.setCandidateId(3001L);

        JobPosition job = new JobPosition();
        job.setId(2001L);
        job.setTitle("高端旅游定制师");
        job.setJd("负责高端客户旅游定制方案");
        job.setRequirements("客户需求挖掘、方案设计、资源协调、服务意识");

        Candidate candidate = new Candidate();
        candidate.setId(3001L);
        candidate.setName("本地冒烟候选人");
        candidate.setResumeText("有销售和客户服务经验");

        return new VolcengineRealtimeSession(
                properties,
                objectMapper,
                mockBrowserSession(browserMessages),
                interviewSession,
                job,
                boundaryConfig,
                List.of(),
                candidate,
                null,
                null,
                null,
                null,
                event -> finishEvents.add((InterviewAutoFinishEvent) event)
        );
    }

    private WebSocketSession mockBrowserSession(List<String> browserMessages) {
        WebSocketSession browserSession = mock(WebSocketSession.class);
        when(browserSession.isOpen()).thenReturn(true);
        try {
            doAnswer(invocation -> {
                WebSocketMessage<?> message = invocation.getArgument(0);
                if (message instanceof TextMessage textMessage) {
                    browserMessages.add(textMessage.getPayload());
                }
                return null;
            }).when(browserSession).sendMessage(any(WebSocketMessage.class));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return browserSession;
    }

    private boolean hasEvent(List<String> messages, String event) throws Exception {
        for (String message : messages) {
            JsonNode node = objectMapper.readTree(message);
            if (event.equals(node.path("event").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(List<String> messages, String text) {
        return messages.stream().anyMatch(message -> message.contains(text));
    }

    private void appendAsrText(VolcengineRealtimeSession realtimeSession, String text) {
        ReflectionTestUtils.invokeMethod(
                realtimeSession,
                "appendRealtimeTextMessage",
                451,
                "{\"results\":[{\"is_interim\":false,\"text\":\"" + text + "\"}]}"
        );
    }

    private void setCurrentCandidateText(VolcengineRealtimeSession realtimeSession, String text) {
        InterviewMessage message = new InterviewMessage();
        message.setRole("CANDIDATE");
        message.setContent(text);
        ReflectionTestUtils.setField(realtimeSession, "currentCandidateMessage", message);
    }

    private record CountingModelWebSocket(AtomicInteger sendCount) implements RealtimeModelWebSocket {

        @Override
        public void sendBinary(ByteBuffer data) {
            sendCount.incrementAndGet();
        }

        @Override
        public void close(String reason) {
        }

        @Override
        public void abort() {
        }
    }
}
