package com.zook.hrinterview.realtime.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.component.ai.interview.AiInterviewBoundaryConfig;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.entity.EvaluationDimension;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.realtime.protocol.VolcengineTtsProtocol;
import com.zook.hrinterview.realtime.event.InterviewAutoFinishEvent;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocket;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketClientFactory;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketListener;
import com.zook.hrinterview.realtime.service.RealtimeMessagePersistService;
import com.zook.hrinterview.utils.InterviewMessageUtils;
import com.zook.hrinterview.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VolcengineRealtimeSession implements RealtimeModelWebSocketListener {

    private static final Logger INTERVIEW_PROCESS_LOGGER = LoggerFactory.getLogger("INTERVIEW_PROCESS_LOG");

    private final VolcengineRealtimeProperties properties;

    private final ObjectMapper objectMapper;

    private final WebSocketSession browserSession;

    private final InterviewSession interviewSession;

    private final JobPosition job;

    private final AiInterviewBoundaryConfig boundaryConfig;

    private final List<EvaluationDimension> evaluationDimensions;

    private final Candidate candidate;

    private final InterviewMessageMapper interviewMessageMapper;

    private final RealtimeMessagePersistService realtimeMessagePersistService;

    private final RedisUtils redisUtils;

    private final RealtimeModelWebSocketClientFactory webSocketClientFactory;

    private final ApplicationEventPublisher eventPublisher;

    private final String realtimeSessionId = UUID.randomUUID().toString();

    private final String connectId = UUID.randomUUID().toString();

    private RealtimeModelWebSocket volcengineSocket;

    private ByteArrayOutputStream partialBinaryMessage;

    private String lastPersistedCandidateText = "";

    private String lastPersistedAiText = "";

    private Long currentPersistedCandidateMessageId;

    private Long currentPersistedAiMessageId;

    private InterviewMessage currentCandidateMessage;

    private InterviewMessage currentAiMessage;

    private CompletableFuture<Void> currentCandidatePersistFuture = CompletableFuture.completedFuture(null);

    private CompletableFuture<Void> currentAiPersistFuture = CompletableFuture.completedFuture(null);

    private final List<CompletableFuture<Void>> pendingPersistFutures = new CopyOnWriteArrayList<>();

    private boolean hasAiChatResponseInTurn;

    private final List<InterviewMessage> historyMessages = new ArrayList<>();

    private boolean resumedSession;

    private boolean interviewQuestionLimitReached;

    private boolean closingFollowUpStarted;

    private int closingFollowUpTurnCount;

    private boolean suppressModelResponseUntilChatEnded;

    private String pendingSystemSpeechAfterSuppressedModel = "";

    private boolean pendingAutoFinishAfterSuppressedModel;

    private int aiQuestionCount;

    private boolean interviewCompletedNoticeSent;

    private boolean autoFinishAfterCurrentSpeech;

    private String pendingAiFinalText = "";

    private String pendingOpeningText = "";

    private boolean pendingOpeningTextPersistable;

    public VolcengineRealtimeSession(
            VolcengineRealtimeProperties properties,
            ObjectMapper objectMapper,
            WebSocketSession browserSession,
            InterviewSession interviewSession,
            JobPosition job,
            AiInterviewBoundaryConfig boundaryConfig,
            List<EvaluationDimension> evaluationDimensions,
            Candidate candidate,
            InterviewMessageMapper interviewMessageMapper,
            RealtimeMessagePersistService realtimeMessagePersistService,
            RedisUtils redisUtils,
            RealtimeModelWebSocketClientFactory webSocketClientFactory,
            ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.browserSession = browserSession;
        this.interviewSession = interviewSession;
        this.job = job;
        this.boundaryConfig = boundaryConfig == null ? new AiInterviewBoundaryConfig() : boundaryConfig;
        this.evaluationDimensions = evaluationDimensions == null ? List.of() : evaluationDimensions;
        this.candidate = candidate;
        this.interviewMessageMapper = interviewMessageMapper;
        this.realtimeMessagePersistService = realtimeMessagePersistService;
        this.redisUtils = redisUtils;
        this.webSocketClientFactory = webSocketClientFactory;
        this.eventPublisher = eventPublisher;
    }

    public void connect() {
        loadHistoryMessages();
        this.volcengineSocket = webSocketClientFactory.select(properties).connect(properties, connectId, this);
        sendBinary(VolcengineTtsProtocol.startConnection());
        sendStartSession();
        if (historyMessages.isEmpty()) {
            pendingOpeningText = buildOpeningText();
            pendingOpeningTextPersistable = true;
        } else {
            pendingOpeningText = buildResumeNoticeText();
            pendingOpeningTextPersistable = false;
        }
        logInterviewProcess("realtime_connect", Map.of(
                "resumed", resumedSession,
                "historyMessageCount", historyMessages.size(),
                "dimensionCount", evaluationDimensions.size()
        ));
    }

    public void sendAudio(byte[] audioChunk) {
        if (audioChunk == null || audioChunk.length == 0) {
            return;
        }
        sendBinary(VolcengineTtsProtocol.audioTaskRequest(realtimeSessionId, audioChunk));
    }

    public void chatTextQuery(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        appendMessage("CANDIDATE", text);
        logInterviewProcess("candidate_text", Map.of(
                "textLength", text.length()
        ));
        PolicyAnswerResult policyAnswer = buildCandidatePolicyAnswer(text);
        if (shouldStopAsking()) {
            handleQuestionLimitCandidateText(text, policyAnswer);
            return;
        }
        if (policyAnswer != null && StringUtils.hasText(policyAnswer.answer())) {
            logInterviewProcess("candidate_policy_answer", Map.of(
                    "policyName", policyAnswer.policyName(),
                    "answerPreview", limitLogText(policyAnswer.answer(), 120)
            ));
            sayHello(policyAnswer.answer(), true);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        sendBinary(VolcengineTtsProtocol.chatTextQuery(realtimeSessionId, toJson(payload)));
    }

    public void sayHello(String text) {
        sayHello(text, true);
    }

    public void sayHello(String text, boolean persist) {
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        if (persist) {
            appendMessage("AI", text);
        }
        sendBinary(VolcengineTtsProtocol.sayHello(realtimeSessionId, toJson(payload)));
    }

    public void finish() {
        logInterviewProcess("finish", Map.of());
        flushCurrentCandidateMessage();
        flushPendingAiMessage();
        flushPendingPersistence(Duration.ofSeconds(3));
        if (volcengineSocket != null) {
            sendBinary(VolcengineTtsProtocol.finishSession(realtimeSessionId));
            sendBinary(VolcengineTtsProtocol.finishConnection());
            volcengineSocket.close("finish");
        }
    }

    public void close() {
        logInterviewProcess("close", Map.of());
        flushCurrentCandidateMessage();
        flushPendingAiMessage();
        flushPendingPersistence(Duration.ofSeconds(2));
        if (volcengineSocket != null) {
            volcengineSocket.abort();
        }
    }

    public void closeBrowser(String reason) {
        try {
            if (browserSession.isOpen()) {
                sendBrowserEvent("interview_closed_by_admin",
                        StringUtils.hasText(reason) ? reason : "面试已由后台关闭",
                        null,
                        0);
                browserSession.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION.withReason(reason));
            }
        } catch (Exception ignored) {
        }
        close();
    }

    public Long getInterviewSessionId() {
        return interviewSession == null ? null : interviewSession.getId();
    }

    @Override
    public void onOpen(RealtimeModelWebSocket webSocket) {
    }

    @Override
    public void onText(String text) {
        sendToBrowser(new TextMessage(text));
    }

    @Override
    public void onBinary(byte[] bytes, boolean last) {
        if (last && partialBinaryMessage == null) {
            handleVolcengineMessage(bytes);
        } else {
            appendPartialBinary(bytes);
            if (last) {
                handleVolcengineMessage(partialBinaryMessage.toByteArray());
                partialBinaryMessage = null;
            }
        }
    }

    @Override
    public void onClose(int statusCode, String reason) {
        sendBrowserEvent("disconnected", "Realtime 模型连接已关闭：" + statusCode + (StringUtils.hasText(reason) ? "，" + reason : ""), null, statusCode);
    }

    @Override
    public void onError(Throwable error) {
        sendBrowserEvent("error", "Realtime 模型连接异常", null, 0);
    }

    private void sendStartSession() {
        Map<String, Object> asrAudioInfo = new LinkedHashMap<>();
        asrAudioInfo.put("format", properties.getInputAudioFormat());
        asrAudioInfo.put("sample_rate", properties.getSampleRate());
        asrAudioInfo.put("channel", properties.getChannel());

        Map<String, Object> asr = new LinkedHashMap<>();
        asr.put("audio_info", asrAudioInfo);
        asr.put("extra", new LinkedHashMap<>());

        Map<String, Object> dialogExtra = new LinkedHashMap<>();
        dialogExtra.put("model", properties.getDialogModel());
        dialogExtra.put("strict_audit", false);
        dialogExtra.put("enable_conversation_truncate", true);

        Map<String, Object> dialog = new LinkedHashMap<>();
        dialog.put("bot_name", "AI面试官");
        dialog.put("dialog_id", String.valueOf(interviewSession.getId()));
        if (isScModel()) {
            dialog.put("character_manifest", buildCharacterManifest());
        } else {
            dialog.put("system_role", buildSystemRole());
            dialog.put("speaking_style", buildSpeakingStyle());
        }
        dialog.put("extra", dialogExtra);

        Map<String, Object> ttsAudioConfig = new LinkedHashMap<>();
        ttsAudioConfig.put("format", properties.getOutputAudioFormat());
        ttsAudioConfig.put("sample_rate", properties.getOutputSampleRate());
        ttsAudioConfig.put("channel", properties.getChannel());

        Map<String, Object> tts = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.getSpeaker())) {
            tts.put("speaker", properties.getSpeaker());
        }
        tts.put("audio_config", ttsAudioConfig);
        tts.put("extra", new LinkedHashMap<>());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asr", asr);
        payload.put("dialog", dialog);
        payload.put("tts", tts);
        sendBinary(VolcengineTtsProtocol.startSession(realtimeSessionId, toJson(payload)));
    }

    private String buildOpeningText() {
        StringBuilder builder = new StringBuilder();
        builder.append("你好，我是本次 AI 面试官，本轮预计会提问约")
                .append(targetQuestionCount())
                .append("个正式问题。");
        if (job != null) {
            builder.append("本次面试岗位是").append(nullToEmpty(job.getTitle())).append("。");
        }
        builder.append("请先用一分钟做一个自我介绍。正式问题结束后系统会自动收尾；如果你想提前结束，也可以点击页面下方的结束面试按钮。");
        return builder.toString();
    }

    private String buildResumeNoticeText() {
        return "实时语音已重新连接，我们继续刚才的面试。正式问题结束后系统会自动收尾；如果你想提前结束，也可以点击页面下方的结束面试按钮。";
    }

    private String buildCharacterManifest() {
        StringBuilder builder = new StringBuilder();
        builder.append("name: AI面试官\n");
        builder.append("role: |\n");
        appendYamlBlock(builder, "你是一名专业、友善、克制的 AI 面试官。");
        builder.append("speaking_style: |\n");
        appendYamlBlock(builder, buildSpeakingStyle());
        builder.append("interview_rules: |\n");
        appendYamlBlock(builder, nullToEmpty(properties.getInstructions()));
        builder.append("interview_pacing_rules: |\n");
        appendYamlBlock(builder, buildInterviewPacingRules());
        appendCandidateQuestionAnswerGuideManifest(builder);
        if (job != null) {
            builder.append("job_title: |\n");
            appendYamlBlock(builder, nullToEmpty(job.getTitle()));
            builder.append("job_description: |\n");
            appendYamlBlock(builder, limit(job.getJd(), 1200));
            builder.append("requirements: |\n");
            appendYamlBlock(builder, limit(job.getRequirements(), 1200));
        }
        appendEvaluationDimensionManifest(builder);
        if (candidate != null) {
            builder.append("candidate_name: |\n");
            appendYamlBlock(builder, nullToEmpty(candidate.getName()));
            if (StringUtils.hasText(candidate.getGender())) {
                builder.append("candidate_gender: |\n");
                appendYamlBlock(builder, candidate.getGender());
            }
            if (candidate.getAge() != null) {
                builder.append("candidate_age: |\n");
                appendYamlBlock(builder, String.valueOf(candidate.getAge()));
            }
            builder.append("candidate_resume: |\n");
            appendYamlBlock(builder, limit(candidate.getResumeText(), 2000));
        }
        appendHistoryManifest(builder);
        return builder.toString();
    }

    private String buildSystemRole() {
        StringBuilder builder = new StringBuilder();
        builder.append(nullToEmpty(properties.getInstructions()));
        builder.append("\n\n面试节奏规则：").append(sanitizeManifestText(buildInterviewPacingRules()));
        appendCandidateQuestionAnswerGuideSystemRole(builder);
        if (job != null) {
            builder.append("\n\n岗位名称：").append(sanitizeManifestText(job.getTitle()));
            builder.append("\n岗位JD：").append(sanitizeManifestText(limit(job.getJd(), 1200)));
            builder.append("\n能力要求：").append(sanitizeManifestText(limit(job.getRequirements(), 1200)));
        }
        appendEvaluationDimensionSystemRole(builder);
        if (candidate != null) {
            builder.append("\n\n候选人姓名：").append(sanitizeManifestText(candidate.getName()));
            if (StringUtils.hasText(candidate.getGender())) {
                builder.append("\n候选人性别：").append(sanitizeManifestText(candidate.getGender()));
            }
            if (candidate.getAge() != null) {
                builder.append("\n候选人年龄：").append(candidate.getAge());
            }
            builder.append("\n候选人简历：").append(sanitizeManifestText(limit(candidate.getResumeText(), 1600)));
        }
        appendHistorySystemRole(builder);
        return builder.toString();
    }

    private String buildSpeakingStyle() {
        return "中文口语表达，简洁、清晰、有礼貌。每次只问一个问题，不要连续追问多个问题。"
                + " 控制节奏，避免围绕同一个回答无限深挖。";
    }

    private String buildInterviewPacingRules() {
        int targetQuestionCount = targetQuestionCount();
        int maxQuestionCount = Math.max(targetQuestionCount, positiveOrDefault(boundaryConfig.getMaxQuestionCount(), positiveOrDefault(properties.getMaxQuestionCount(), 12)));
        int maxFollowUpPerTopic = positiveOrDefault(boundaryConfig.getMaxFollowUpPerTopic(), positiveOrDefault(properties.getMaxFollowUpPerTopic(), 2));
        int closingFollowUpTurnLimit = closingFollowUpTurnLimit();
        return "本场面试不是固定题库，但必须采用“有限问题数 + 能力覆盖优先”的策略。"
                + "开场自我介绍只是引导，不计入目标提问数和最大提问数。正式问题从候选人完成自我介绍后的第一问开始计算。"
                + "正式问题目标约 " + targetQuestionCount + " 个，正式问题最多不超过 " + maxQuestionCount + " 个。"
                + "请把问题名额当成预算使用，不要因为一个回答有可追问空间就一直追问。"
                + "同一个能力点、同一个项目或同一个经历最多连续追问 " + maxFollowUpPerTopic + " 次；如果候选人已经给出清楚结论、数据、案例，或者表示不了解，应立即切换到下一个能力点。"
                + "提问顺序按阶段推进：1个开场自我介绍；覆盖岗位核心能力；2个过往案例或项目经验；1-2个岗位关键短板或风险；1个协作、服务意识或交付稳定性；最后1个收尾补充。"
                + "如果提供了评分维度，每次提问前必须判断哪些评分维度还没有覆盖，优先问未覆盖维度；每个评分维度至少覆盖1个主问题，权重大或证据弱的维度最多追问1次，没覆盖完不要提前收尾。"
                + "如果没有提供评分维度，不要编造固定维度表；请根据岗位 JD、能力要求和候选人简历动态提炼 6-8 个核心能力点，优先覆盖未问过的能力点。"
                + "如果岗位是高端旅游定制师，优先覆盖：客户需求挖掘、目的地/产品理解、行程方案设计、预算与资源协调、高净值客户沟通、突发情况处理、成交转化与服务意识。"
                + "候选人表示不想继续某个方向、回答明显疲惫或问还有多少问题时，不要继续纠缠该方向，应进入收尾或切换到最关键的未覆盖能力点。"
                + "接近目标问题数后进入收尾，只再问一个最关键的补充问题。"
                + "达到最大问题数后不要继续提出新的正式面试问题；候选人回答完最后一个正式问题后，系统会进入收尾补充/反问窗口。"
                + "收尾窗口最多允许候选人再交流 " + closingFollowUpTurnLimit + " 轮，期间只能回答候选人的补充或反问，不要追加新的能力考察问题。";
    }

    private void appendCandidateQuestionAnswerGuideManifest(StringBuilder builder) {
        String guide = candidateQuestionAnswerGuide();
        if (!StringUtils.hasText(guide)) {
            return;
        }
        builder.append("candidate_question_answer_guide: |\n");
        appendYamlBlock(builder, "候选人反问内容命中下面任意口径左侧关键词时，只能按对应右侧口径回答；口径没有写明的信息，不要猜测，不要编造，回答“这个需要以 HR 后续正式沟通为准”。如果口径要求线下面试 HR 回答，必须直接引导候选人线下确认，不要补充金额、比例或规则。回答候选人反问时不要在同一轮追加新的面试问题；回答完只问“还有其他想了解的吗？”。\n" + guide);
    }

    private void appendCandidateQuestionAnswerGuideSystemRole(StringBuilder builder) {
        String guide = candidateQuestionAnswerGuide();
        if (!StringUtils.hasText(guide)) {
            return;
        }
        builder.append("\n\n候选人反问回答口径：")
                .append("候选人反问内容命中以下口径左侧任意关键词时，只能按对应右侧口径回答；口径没有写明的信息，不要猜测，不要编造，回答“这个需要以 HR 后续正式沟通为准”。如果口径要求线下面试 HR 回答，必须直接引导候选人线下确认，不要补充金额、比例或规则。")
                .append("回答候选人反问时不要在同一轮追加新的面试问题；回答完只问“还有其他想了解的吗？”。")
                .append(sanitizeManifestText(guide));
    }

    private String candidateQuestionAnswerGuide() {
        return boundaryConfig == null ? "" : boundaryConfig.getCandidateQuestionAnswerGuide();
    }

    private PolicyAnswerResult buildCandidatePolicyAnswer(String text) {
        String normalized = normalizeMessageContent(text);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        PolicyAnswerRule rule = matchPolicyAnswerRule(normalized);
        if (rule != null) {
            return new PolicyAnswerResult(rule.displayName(), formatPolicyAnswer(rule));
        }
        if (containsAny(normalized, "试岗", "试用期", "薪资", "薪酬", "薪水", "工资", "底薪", "提成", "业绩", "奖金", "绩效",
                "发薪", "发工资", "上班", "下班", "上下班", "工作时间", "作息", "休息时间", "大小周", "双休", "单休",
                "福利", "调休", "加班", "社保", "公积金")) {
            return new PolicyAnswerResult("未配置兜底", "这个需要以 HR 后续正式沟通为准。还有其他想了解的内容可以继续问我。");
        }
        return null;
    }

    private PolicyAnswerRule matchPolicyAnswerRule(String normalizedQuestion) {
        String guide = candidateQuestionAnswerGuide();
        if (!StringUtils.hasText(guide)) {
            return null;
        }
        PolicyAnswerRule bestRule = null;
        int bestScore = 0;
        for (String item : guide.split("[\\n。；;]+")) {
            PolicyAnswerRule rule = parsePolicyAnswerRule(item);
            int score = rule == null ? 0 : rule.matchScore(normalizedQuestion);
            if (score > bestScore) {
                bestRule = rule;
                bestScore = score;
            }
        }
        return bestRule;
    }

    private PolicyAnswerRule parsePolicyAnswerRule(String item) {
        if (!StringUtils.hasText(item)) {
            return null;
        }
        Matcher matcher = Pattern.compile("^\\s*([^:：]+)[:：](.+)$").matcher(item.trim());
        if (!matcher.matches()) {
            return null;
        }
        String label = normalizeMessageContent(matcher.group(1));
        String answer = normalizeMessageContent(matcher.group(2));
        if (!StringUtils.hasText(label) || !StringUtils.hasText(answer)) {
            return null;
        }
        return new PolicyAnswerRule(label, answer);
    }

    private String formatPolicyAnswer(PolicyAnswerRule rule) {
        String answer = rule.answer();
        if (!endsWithSentenceMark(answer)) {
            answer += "。";
        }
        return rule.displayName() + "：" + answer + continueQuestionSuffix();
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithSentenceMark(String text) {
        return StringUtils.hasText(text)
                && (text.endsWith("。") || text.endsWith("！") || text.endsWith("？") || text.endsWith("!") || text.endsWith("?"));
    }

    private record PolicyAnswerRule(String label, String answer) {

        private String displayName() {
            return label.split("[/／|]", 2)[0].trim();
        }

        private int matchScore(String question) {
            if (!StringUtils.hasText(question)) {
                return 0;
            }
            String normalizedLabel = label.replace("候选人询问", "")
                    .replace("等薪酬相关问题", "")
                    .replace("等公司制度", "");
            int score = 0;
            score = Math.max(score, derivedMatchScore(normalizedLabel, question));
            if (StringUtils.hasText(normalizedLabel) && question.contains(normalizedLabel)) {
                score = normalizedLabel.length();
            }
            for (String keyword : normalizedLabel.split("[、,，/／|或和及\\s]+")) {
                if (keyword.length() >= 2 && question.contains(keyword)) {
                    score = Math.max(score, keyword.length());
                }
                if ("上下班".equals(keyword) && (question.contains("上班") || question.contains("下班"))) {
                    score = Math.max(score, keyword.length());
                }
            }
            return score;
        }

        private int derivedMatchScore(String normalizedLabel, String question) {
            if (!StringUtils.hasText(normalizedLabel) || !StringUtils.hasText(question)) {
                return 0;
            }
            if (containsAnyText(normalizedLabel, "工资发放时间", "发薪日", "几号发工资")
                    && containsAnyText(question, "工资", "薪水", "薪资", "薪酬", "发薪")
                    && containsAnyText(question, "发", "发放", "什么时候", "啥时候", "几号", "哪天", "日期", "时间")) {
                return 20;
            }
            if (containsAnyText(normalizedLabel, "作息时间", "上下班", "休息时间", "大小周")
                    && containsAnyText(question, "上班", "下班", "上下班", "工作时间", "作息", "休息", "大小周", "双休", "单休")) {
                return 18;
            }
            return 0;
        }

        private static boolean containsAnyText(String text, String... keywords) {
            if (!StringUtils.hasText(text) || keywords == null) {
                return false;
            }
            for (String keyword : keywords) {
                if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record PolicyAnswerResult(String policyName, String answer) {
    }

    private void appendEvaluationDimensionManifest(StringBuilder builder) {
        if (evaluationDimensions.isEmpty()) {
            return;
        }
        builder.append("evaluation_dimensions: |\n");
        appendYamlBlock(builder, buildEvaluationDimensionText());
    }

    private void appendEvaluationDimensionSystemRole(StringBuilder builder) {
        if (evaluationDimensions.isEmpty()) {
            return;
        }
        builder.append("\n评分维度：").append(sanitizeManifestText(buildEvaluationDimensionText()));
    }

    private String buildEvaluationDimensionText() {
        StringBuilder builder = new StringBuilder();
        for (EvaluationDimension dimension : evaluationDimensions) {
            if (dimension == null || !StringUtils.hasText(dimension.getName())) {
                continue;
            }
            builder.append("- ").append(dimension.getName());
            if (dimension.getWeight() != null) {
                builder.append("，权重").append(dimension.getWeight()).append("%");
            }
            if (StringUtils.hasText(dimension.getDescription())) {
                builder.append("，说明：").append(dimension.getDescription());
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private void loadHistoryMessages() {
        if (interviewMessageMapper == null || interviewSession == null || interviewSession.getId() == null) {
            return;
        }
        try {
            List<InterviewMessage> messages = interviewMessageMapper.selectList(
                    Wrappers.lambdaQuery(InterviewMessage.class)
                            .eq(InterviewMessage::getSessionId, interviewSession.getId())
                            .orderByDesc(InterviewMessage::getSequenceNo)
                            .orderByDesc(InterviewMessage::getId)
                            .last("limit 20"));
            Collections.reverse(messages);
            messages = mergeDbAndBufferedMessages(messages);
            List<InterviewMessage> mergedMessages = InterviewMessageUtils.mergeAdjacentSameRole(messages);
            historyMessages.clear();
            historyMessages.addAll(mergedMessages);
            aiQuestionCount = countAiQuestions(mergedMessages);
            interviewQuestionLimitReached = aiQuestionCount >= maxQuestionCount();
            interviewCompletedNoticeSent = mergedMessages.stream()
                    .anyMatch(message -> "AI".equals(message.getRole())
                            && message.getContent() != null
                            && message.getContent().contains("本轮问题已经了解得差不多"));
            syncRedisMessageSequence(messages);
            resumedSession = historyMessages.stream()
                    .anyMatch(message -> "CANDIDATE".equals(message.getRole()) && StringUtils.hasText(message.getContent()));
        } catch (Exception ignored) {
            historyMessages.clear();
            resumedSession = false;
            aiQuestionCount = 0;
            interviewQuestionLimitReached = false;
            interviewCompletedNoticeSent = false;
        }
    }

    private List<InterviewMessage> mergeDbAndBufferedMessages(List<InterviewMessage> dbMessages) {
        Map<Integer, InterviewMessage> messageMap = new LinkedHashMap<>();
        for (InterviewMessage message : dbMessages == null ? List.<InterviewMessage>of() : dbMessages) {
            if (message.getSequenceNo() != null) {
                messageMap.put(message.getSequenceNo(), message);
            }
        }
        if (realtimeMessagePersistService != null) {
            List<InterviewMessage> bufferedMessages = realtimeMessagePersistService.listBufferedMessages(interviewSession.getId());
            for (InterviewMessage bufferedMessage : bufferedMessages) {
                if (bufferedMessage.getSequenceNo() == null) {
                    continue;
                }
                InterviewMessage existing = messageMap.get(bufferedMessage.getSequenceNo());
                if (existing == null || contentLength(bufferedMessage) >= contentLength(existing)) {
                    messageMap.put(bufferedMessage.getSequenceNo(), bufferedMessage);
                }
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
                .toList();
    }

    private int contentLength(InterviewMessage message) {
        return message == null || message.getContent() == null ? 0 : message.getContent().length();
    }

    private void appendHistoryManifest(StringBuilder builder) {
        if (!resumedSession || historyMessages.isEmpty()) {
            return;
        }
        builder.append("conversation_history: |\n");
        appendYamlBlock(builder, buildHistoryText());
        builder.append("resume_instruction: |\n");
        appendYamlBlock(builder, "这是断线重连后的续接面试。请基于上面的历史对话继续，不要重复开场白，不要再次要求候选人做自我介绍，不要主动说“我们继续刚才的面试”。续接后优先判断未覆盖能力点，不要默认围绕断线前最后一个话题继续深挖。如果候选人继续回答，则顺着候选人的回答只问一个新的问题。");
    }

    private void appendHistorySystemRole(StringBuilder builder) {
        if (!resumedSession || historyMessages.isEmpty()) {
            return;
        }
        builder.append("\n\n历史对话：\n").append(sanitizeManifestText(buildHistoryText()));
        builder.append("\n\n续接要求：这是断线重连后的续接面试，请基于历史对话继续，不要重复开场白，不要再次要求候选人做自我介绍，不要主动说“我们继续刚才的面试”。续接后优先判断未覆盖能力点，不要默认围绕断线前最后一个话题继续深挖。如果候选人继续回答，则顺着候选人的回答只问一个新的问题。");
    }

    private String buildHistoryText() {
        StringBuilder builder = new StringBuilder();
        for (InterviewMessage message : historyMessages) {
            String content = sanitizeManifestText(limit(message.getContent(), 300));
            if (!StringUtils.hasText(content)) {
                continue;
            }
            builder.append(roleLabel(message.getRole())).append("：").append(content).append('\n');
        }
        return builder.toString().trim();
    }

    private String roleLabel(String role) {
        if ("CANDIDATE".equals(role)) {
            return "候选人";
        }
        if ("AI".equals(role)) {
            return "AI面试官";
        }
        return nullToEmpty(role);
    }

    private boolean isScModel() {
        return "2.2.0.0".equals(properties.getDialogModel());
    }

    private void appendYamlBlock(StringBuilder builder, String value) {
        String safeValue = sanitizeManifestText(value);
        if (!StringUtils.hasText(safeValue)) {
            builder.append("  无\n");
            return;
        }
        for (String line : safeValue.split("\n")) {
            builder.append("  ").append(line).append('\n');
        }
    }

    private String sanitizeManifestText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\\', '/')
                .replace('\t', ' ')
                .replace('\r', '\n')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void handleVolcengineMessage(byte[] bytes) {
        try {
            VolcengineTtsProtocol.ParsedMessage message = VolcengineTtsProtocol.parse(bytes);
            if (message.getMsgType() == VolcengineTtsProtocol.MSG_TYPE_AUDIO_ONLY_SERVER) {
                if (suppressModelResponseUntilChatEnded) {
                    return;
                }
                sendAudioToBrowser(message.getPayload());
                return;
            }
            if (message.getMsgType() == VolcengineTtsProtocol.MSG_TYPE_ERROR) {
                String payloadText = payloadToText(message);
                String event = isQuotaExceededError(payloadText) ? "quota_exceeded" : "error";
                logInterviewProcess("model_error", Map.of(
                        "eventCode", message.getEvent(),
                        "message", limitLogText(friendlyRealtimeError(payloadText), 160)
                ));
                sendBrowserEvent(event, friendlyRealtimeError(payloadText), payloadText, message.getEvent());
                return;
            }
            if (message.getEvent() == 352 && message.getPayload() != null && message.getPayload().length > 0) {
                if (suppressModelResponseUntilChatEnded) {
                    return;
                }
                sendAudioToBrowser(message.getPayload());
                return;
            }
            if (message.getEvent() == 150) {
                sendPendingOpeningText();
            }
            if (message.getPayload() != null && message.getPayload().length > 0) {
                String payloadText = payloadToText(message);
                if (!appendRealtimeTextMessage(message.getEvent(), payloadText)) {
                    return;
                }
                sendBrowserEvent(eventName(message.getEvent()), null, payloadText, message.getEvent());
            }
        } catch (Exception ex) {
            String errorMessage = ex.getMessage();
            String event = isQuotaExceededError(errorMessage) ? "quota_exceeded" : "error";
            logInterviewProcess("model_parse_error", Map.of(
                    "message", limitLogText(errorMessage == null ? "" : errorMessage, 160)
            ));
            sendBrowserEvent(event, "火山 Realtime 响应解析失败", errorMessage, 0);
        }
    }

    private void sendPendingOpeningText() {
        if (!StringUtils.hasText(pendingOpeningText)) {
            return;
        }
        String text = pendingOpeningText;
        boolean persist = pendingOpeningTextPersistable;
        pendingOpeningText = "";
        pendingOpeningTextPersistable = false;
        sayHello(text, persist);
    }

    private void sendAudioToBrowser(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        sendToBrowser(new TextMessage("audio:" + Base64.getEncoder().encodeToString(payload)));
    }

    private boolean appendRealtimeTextMessage(int event, String payloadText) {
        if (!StringUtils.hasText(payloadText)) {
            return true;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(payloadText, Map.class);
            if (event == 451) {
                String text = extractAsrText(payload);
                if (StringUtils.hasText(text)) {
                    resetAiPersistTurn();
                    appendOrUpdateCandidateMessage(text);
                }
                return true;
            }
            if (event == 459) {
                String text = extractAsrText(payload);
                if (StringUtils.hasText(text)) {
                    resetAiPersistTurn();
                    appendOrUpdateCandidateMessage(text);
                }
                if (shouldStopAsking()) {
                    if (!hasCurrentCandidateMessageText()) {
                        logInterviewProcess("candidate_empty_final_ignored", Map.of(
                                "closingFollowUpStarted", closingFollowUpStarted,
                                "closingFollowUpTurnCount", closingFollowUpTurnCount,
                                "closingFollowUpTurnLimit", closingFollowUpTurnLimit()
                        ));
                        return true;
                    }
                    String closingText = currentCandidateMessage == null ? "" : currentCandidateMessage.getContent();
                    handleQuestionLimitCandidateText(closingText, buildCandidatePolicyAnswer(closingText), true);
                    return true;
                }
                flushCurrentCandidateMessage();
                return true;
            }
            if ((event == 550 || event == 350) && suppressModelResponseUntilChatEnded) {
                return false;
            }
            if ((event == 559 || event == 351 || event == 359) && suppressModelResponseUntilChatEnded) {
                if (event == 359) {
                    finishSuppressingModelAndSpeakPendingSystemText();
                }
                if (event == 559 && !StringUtils.hasText(pendingSystemSpeechAfterSuppressedModel)) {
                    suppressModelResponseUntilChatEnded = false;
                }
                if (event == 559) {
                    finishSuppressingModelAndSpeakPendingSystemText();
                }
                return false;
            }
            if (event == 550) {
                Object content = payload.get("content");
                if (content == null) {
                    content = payload.get("text");
                }
                if (content != null && StringUtils.hasText(String.valueOf(content))) {
                    if (shouldBlockNextAiQuestion(String.valueOf(content))) {
                        suppressBlockedModelQuestion();
                        return false;
                    }
                    flushCurrentCandidateMessage();
                    hasAiChatResponseInTurn = true;
                    pendingAiFinalText = mergeMessageContent(pendingAiFinalText, String.valueOf(content));
                    markClosingWindowIfModelStartedClosing(String.valueOf(content));
                    if (isModelCompletionAnswer(String.valueOf(content))) {
                        autoFinishAfterCurrentSpeech = true;
                    }
                }
                return true;
            }
            if (event == 351 || event == 359) {
                flushPendingAiMessage();
                if (event == 359) {
                    completeAutoFinishAfterSpeech();
                }
                return true;
            }
            if (event == 350 && !hasAiChatResponseInTurn) {
                Object content = payload.get("content");
                if (content == null) {
                    content = payload.get("text");
                }
                if (content != null && StringUtils.hasText(String.valueOf(content))) {
                    if (shouldBlockNextAiQuestion(String.valueOf(content))) {
                        suppressBlockedModelQuestion();
                        return false;
                    }
                    flushCurrentCandidateMessage();
                    hasAiChatResponseInTurn = true;
                    pendingAiFinalText = mergeMessageContent(pendingAiFinalText, String.valueOf(content));
                    markClosingWindowIfModelStartedClosing(String.valueOf(content));
                    if (isModelCompletionAnswer(String.valueOf(content))) {
                        autoFinishAfterCurrentSpeech = true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private String extractAsrText(Map<?, ?> payload) {
        Object results = payload.get("results");
        if (!(results instanceof java.util.List<?> list)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> result)) {
                continue;
            }
            Object interim = result.get("is_interim");
            if (Boolean.TRUE.equals(interim)) {
                continue;
            }
            Object text = result.get("text");
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private void appendMessage(String role, String content) {
        if (!StringUtils.hasText(content) || interviewMessageMapper == null) {
            return;
        }
        if ("CANDIDATE".equals(role)) {
            resetAiPersistTurn();
            currentPersistedCandidateMessageId = null;
        }
        String normalized = normalizeMessageContent(content);
        if (!StringUtils.hasText(normalized) || isDuplicateMessage(role, normalized)) {
            return;
        }
        try {
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole(role);
            message.setContent(normalized);
            message.setSequenceNo(nextSequenceNo());
            persistInsert(message);
            rememberPersistedMessage(role, normalized);
            if ("AI".equals(role) && isCountableInterviewQuestion(normalized)) {
                aiQuestionCount += 1;
                interviewQuestionLimitReached = aiQuestionCount >= maxQuestionCount();
            }
        } catch (Exception ignored) {
        }
    }

    private void appendOrUpdateCandidateMessage(String content) {
        if (!StringUtils.hasText(content) || interviewMessageMapper == null) {
            return;
        }
        String normalized = normalizeMessageContent(content);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        try {
            if (currentCandidateMessage != null) {
                InterviewMessage existing = currentCandidateMessage;
                if (existing != null) {
                    String merged = mergeMessageContent(existing.getContent(), normalized);
                    if (StringUtils.hasText(merged)) {
                        existing.setContent(merged);
                    }
                    return;
                }
            }
            if (isDuplicateMessage("CANDIDATE", normalized)) {
                return;
            }
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole("CANDIDATE");
            message.setContent(normalized);
            currentCandidateMessage = message;
        } catch (Exception ignored) {
        }
    }

    private void appendOrUpdateAiMessage(String content) {
        if (!StringUtils.hasText(content) || interviewMessageMapper == null) {
            return;
        }
        String normalized = normalizeMessageContent(content);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        try {
            flushCurrentCandidateMessage();
            currentPersistedCandidateMessageId = null;
            currentCandidateMessage = null;
            if (currentAiMessage != null) {
                InterviewMessage existing = currentAiMessage;
                if (existing != null) {
                    String merged = mergeMessageContent(existing.getContent(), normalized);
                    if (StringUtils.hasText(merged) && !merged.equals(existing.getContent())) {
                        existing.setContent(merged);
                        currentAiPersistFuture = persistUpdateAfter(currentAiPersistFuture, existing);
                        rememberPersistedMessage("AI", merged);
                    }
                    return;
                }
            }
            if (isDuplicateMessage("AI", normalized)) {
                return;
            }
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole("AI");
            message.setContent(normalized);
            message.setSequenceNo(nextSequenceNo());
            currentAiMessage = message;
            currentAiPersistFuture = persistInsert(message);
            currentPersistedAiMessageId = message.getId();
            rememberPersistedMessage("AI", normalized);
            if (isCountableInterviewQuestion(normalized)) {
                aiQuestionCount += 1;
                interviewQuestionLimitReached = aiQuestionCount >= maxQuestionCount();
            }
        } catch (Exception ignored) {
        }
    }

    private void flushPendingAiMessage() {
        if (!StringUtils.hasText(pendingAiFinalText)) {
            return;
        }
        String text = pendingAiFinalText;
        pendingAiFinalText = "";
        appendOrUpdateAiMessage(text);
    }

    private void flushCurrentCandidateMessage() {
        if (currentCandidateMessage == null || interviewMessageMapper == null) {
            return;
        }
        String normalized = normalizeMessageContent(currentCandidateMessage.getContent());
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        currentCandidateMessage.setContent(normalized);
        if (currentCandidateMessage.getSequenceNo() == null) {
            if (isDuplicateMessage("CANDIDATE", normalized)) {
                return;
            }
            currentCandidateMessage.setSequenceNo(nextSequenceNo());
            currentCandidatePersistFuture = persistInsert(currentCandidateMessage);
        } else if (!normalized.equals(lastPersistedCandidateText)) {
            currentCandidatePersistFuture = persistUpdateAfter(currentCandidatePersistFuture, currentCandidateMessage);
        }
        currentPersistedCandidateMessageId = currentCandidateMessage.getId();
        rememberPersistedMessage("CANDIDATE", normalized);
    }

    private boolean hasCurrentCandidateMessageText() {
        return currentCandidateMessage != null
                && StringUtils.hasText(normalizeMessageContent(currentCandidateMessage.getContent()));
    }

    private boolean shouldStopAsking() {
        if (interviewQuestionLimitReached) {
            return true;
        }
        interviewQuestionLimitReached = aiQuestionCount >= maxQuestionCount();
        return interviewQuestionLimitReached;
    }

    private boolean shouldBlockNextAiQuestion(String content) {
        return aiQuestionCount >= maxQuestionCount() && isCountableInterviewQuestion(content);
    }

    private void handleQuestionLimitCandidateText(String text, PolicyAnswerResult policyAnswer) {
        handleQuestionLimitCandidateText(text, policyAnswer, false);
    }

    private void handleQuestionLimitCandidateText(String text, PolicyAnswerResult policyAnswer, boolean deferUntilModelEnded) {
        flushCurrentCandidateMessage();
        logInterviewProcess("question_limit_candidate_turn", Map.of(
                "closingFollowUpStarted", closingFollowUpStarted,
                "closingFollowUpTurnCount", closingFollowUpTurnCount,
                "closingFollowUpTurnLimit", closingFollowUpTurnLimit()
        ));
        if (!closingFollowUpStarted) {
            startClosingFollowUpWindow(deferUntilModelEnded);
            return;
        }
        handleClosingFollowUpTurn(policyAnswer, deferUntilModelEnded);
    }

    private void startClosingFollowUpWindow() {
        startClosingFollowUpWindow(false);
    }

    private void startClosingFollowUpWindow(boolean deferUntilModelEnded) {
        closingFollowUpStarted = true;
        if (closingFollowUpTurnLimit() <= 0) {
            sendInterviewCompletedNotice(null, deferUntilModelEnded);
            return;
        }
        String notice = "正式面试问题已经结束。你可以再补充说明，或咨询关于岗位安排的问题，最多还可以进行"
                + closingFollowUpTurnLimit()
                + "次交流，之后系统会自动结束面试。";
        speakSystemText(notice, deferUntilModelEnded);
    }

    private void handleClosingFollowUpTurn(PolicyAnswerResult policyAnswer) {
        handleClosingFollowUpTurn(policyAnswer, false);
    }

    private void handleClosingFollowUpTurn(PolicyAnswerResult policyAnswer, boolean deferUntilModelEnded) {
        closingFollowUpTurnCount += 1;
        boolean lastClosingTurn = closingFollowUpTurnCount >= closingFollowUpTurnLimit();
        String answer = policyAnswer != null && StringUtils.hasText(policyAnswer.answer())
                ? policyAnswer.answer()
                : (lastClosingTurn ? "好的，已记录你的补充。" : "好的，已记录你的补充。你还可以继续补充或咨询岗位安排。");
        if (!lastClosingTurn) {
            speakSystemText(answer, deferUntilModelEnded);
            return;
        }
        sendInterviewCompletedNotice(answer, deferUntilModelEnded);
    }

    private void sendInterviewCompletedNotice() {
        sendInterviewCompletedNotice(null, false);
    }

    private void sendInterviewCompletedNotice(String answerBeforeNotice) {
        sendInterviewCompletedNotice(answerBeforeNotice, false);
    }

    private void sendInterviewCompletedNotice(String answerBeforeNotice, boolean deferUntilModelEnded) {
        String notice = "本轮面试到这里结束，后续结果将由 HR 通知。";
        logInterviewProcess("question_limit_reached", Map.of(
                "noticeSentBefore", interviewCompletedNoticeSent
        ));
        if (!interviewCompletedNoticeSent) {
            String finalText = buildFinalAutoFinishText(answerBeforeNotice, notice);
            if (deferUntilModelEnded) {
                pendingSystemSpeechAfterSuppressedModel = finalText;
                pendingAutoFinishAfterSuppressedModel = true;
                suppressModelResponseUntilChatEnded = true;
            } else {
                speakSystemText(finalText, false);
                autoFinishAfterCurrentSpeech = true;
            }
            interviewCompletedNoticeSent = true;
        }
        if (volcengineSocket == null) {
            completeAutoFinishAfterSpeech();
        }
    }

    private void speakSystemText(String text, boolean deferUntilModelEnded) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (deferUntilModelEnded) {
            clearBrowserAudioQueue();
            pendingSystemSpeechAfterSuppressedModel = text;
            suppressModelResponseUntilChatEnded = true;
            return;
        }
        clearBrowserAudioQueue();
        sayHello(text, true);
        sendAiTextToBrowser(text);
    }

    private void finishSuppressingModelAndSpeakPendingSystemText() {
        if (!suppressModelResponseUntilChatEnded) {
            return;
        }
        suppressModelResponseUntilChatEnded = false;
        String text = pendingSystemSpeechAfterSuppressedModel;
        pendingSystemSpeechAfterSuppressedModel = "";
        if (StringUtils.hasText(text)) {
            clearBrowserAudioQueue();
            sayHello(text, true);
            sendAiTextToBrowser(text);
            if (pendingAutoFinishAfterSuppressedModel) {
                autoFinishAfterCurrentSpeech = true;
                pendingAutoFinishAfterSuppressedModel = false;
            }
        }
    }

    private void clearBrowserAudioQueue() {
        sendBrowserEvent("audio_clear", null, null, 0);
    }

    private void suppressBlockedModelQuestion() {
        suppressModelResponseUntilChatEnded = true;
        if (!closingFollowUpStarted && !interviewCompletedNoticeSent) {
            startClosingFollowUpWindow(true);
        }
    }

    private String buildFinalAutoFinishText(String answerBeforeNotice, String notice) {
        String answer = normalizeMessageContent(answerBeforeNotice);
        if (!StringUtils.hasText(answer)) {
            return notice;
        }
        answer = stripContinueQuestionSuffix(answer);
        if (!endsWithSentenceMark(answer)) {
            answer += "。";
        }
        return answer + notice;
    }

    private String stripContinueQuestionSuffix(String answer) {
        String suffix = continueQuestionSuffix();
        if (StringUtils.hasText(answer) && answer.endsWith(suffix)) {
            return answer.substring(0, answer.length() - suffix.length()).trim();
        }
        return answer;
    }

    private String continueQuestionSuffix() {
        return "还有其他想了解的内容可以继续问我。";
    }

    private void completeAutoFinishAfterSpeech() {
        if (!autoFinishAfterCurrentSpeech) {
            return;
        }
        autoFinishAfterCurrentSpeech = false;
        String notice = "本轮面试到这里结束，后续结果将由 HR 通知。";
        flushCurrentCandidateMessage();
        flushPendingAiMessage();
        flushPendingPersistence(Duration.ofSeconds(3));
        if (volcengineSocket != null) {
            sendBinary(VolcengineTtsProtocol.finishSession(realtimeSessionId));
            sendBinary(VolcengineTtsProtocol.finishConnection());
            volcengineSocket.close("question limit reached");
            volcengineSocket = null;
        }
        sendBrowserEvent("interview_auto_finished", notice, null, 0);
        publishAutoFinishEvent("question_limit_reached");
        closeBrowserAfterAutoFinish();
    }

    private void publishAutoFinishEvent(String reason) {
        if (eventPublisher == null || interviewSession == null || interviewSession.getId() == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new InterviewAutoFinishEvent(interviewSession.getId(), reason));
        } catch (Exception ex) {
            INTERVIEW_PROCESS_LOGGER.warn("Interview auto finish event failed, sessionId={}, reason={}",
                    interviewSession.getId(), reason, ex);
        }
    }

    private void closeBrowserAfterAutoFinish() {
        try {
            if (browserSession.isOpen()) {
                browserSession.close(org.springframework.web.socket.CloseStatus.NORMAL.withReason("面试已自动结束"));
            }
        } catch (Exception ignored) {
        }
    }

    private int maxQuestionCount() {
        return Math.max(targetQuestionCount(), positiveOrDefault(boundaryConfig.getMaxQuestionCount(), positiveOrDefault(properties.getMaxQuestionCount(), 12)));
    }

    private int closingFollowUpTurnLimit() {
        Integer configured = boundaryConfig == null ? null : boundaryConfig.getClosingFollowUpTurnLimit();
        int value = configured == null ? nonNegativeOrDefault(properties.getClosingFollowUpTurnLimit(), 1) : configured;
        return Math.max(0, Math.min(value, 5));
    }

    private int targetQuestionCount() {
        return positiveOrDefault(boundaryConfig.getTargetQuestionCount(), positiveOrDefault(properties.getTargetQuestionCount(), 8));
    }

    private int countAiQuestions(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (InterviewMessage message : messages) {
            if (message != null && "AI".equals(message.getRole()) && isCountableInterviewQuestion(message.getContent())) {
                count += 1;
            }
        }
        return count;
    }

    private boolean isCountableInterviewQuestion(String content) {
        if (!looksLikeQuestion(content)) {
            return false;
        }
        String text = normalizeMessageContent(content);
        return !isOpeningSelfIntroductionPrompt(text);
    }

    private boolean isOpeningSelfIntroductionPrompt(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("自我介绍")
                || text.contains("介绍一下你自己")
                || text.contains("介绍一下自己")
                || text.contains("请先用一分钟")
                || text.contains("先用一分钟");
    }

    private boolean looksLikeQuestion(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String text = normalizeMessageContent(content);
        if (isNonAssessmentAiMessage(text)) {
            return false;
        }
        if (text.contains("本轮问题已经了解得差不多") || text.contains("今天的面试就到这里")) {
            return false;
        }
        return text.contains("？")
                || text.contains("?")
                || text.contains("能不能")
                || text.contains("可以具体")
                || text.contains("说说")
                || text.contains("怎么")
                || text.contains("如何")
                || text.contains("有没有")
                || text.contains("会不会")
                || text.contains("你会");
    }

    private boolean isNonAssessmentAiMessage(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("正式面试问题已经结束")
                || text.contains("本轮面试到这里结束")
                || text.contains("后续结果将由 HR 通知")
                || text.contains("还有其他想了解")
                || text.contains("以 HR 后续正式沟通为准")
                || text.contains("由线下面试 HR")
                || text.contains("已记录你的补充");
    }

    private void markClosingWindowIfModelStartedClosing(String content) {
        String text = normalizeMessageContent(content);
        if (!isModelClosingWindowPrompt(text)) {
            return;
        }
        closingFollowUpStarted = true;
        interviewQuestionLimitReached = true;
    }

    private boolean isModelClosingWindowPrompt(String text) {
        if (!StringUtils.hasText(text) || isModelCompletionAnswer(text)) {
            return false;
        }
        return (containsAny(text, "核心问题", "正式问题", "技术面试", "本轮面试", "问题就差不多", "问题差不多")
                && containsAny(text, "结束", "收尾", "到这里"))
                || (containsAny(text, "还有什么想补充", "有什么想补充", "还有其他想补充", "还有什么疑问", "有什么疑问", "其他疑问", "其他想了解")
                && containsAny(text, "结束", "收尾", "到这里", "如果没有"));
    }

    private boolean isModelCompletionAnswer(String content) {
        String text = normalizeMessageContent(content);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return containsAny(text, "已经结束", "已结束", "面试到这里就结束", "面试就到此结束", "本轮面试就到此结束", "本轮面试到这里结束")
                || (containsAny(text, "感谢你的配合", "感谢你的时间", "祝你求职顺利", "祝你一切顺利", "保持手机畅通", "耐心等消息")
                && containsAny(text, "结果", "通知", "HR", "三个工作日", "后续"));
    }

    private int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private void resetAiPersistTurn() {
        currentPersistedAiMessageId = null;
        currentAiMessage = null;
        hasAiChatResponseInTurn = false;
    }

    private int nextSequenceNo() {
        if (redisUtils == null || interviewSession == null || interviewSession.getId() == null) {
            return 1;
        }
        Long sequenceNo = redisUtils.increment(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId());
        int databaseMaxSequenceNo = queryDatabaseMaxSequenceNo();
        if (sequenceNo != null && sequenceNo <= databaseMaxSequenceNo) {
            redisUtils.set(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId(), databaseMaxSequenceNo);
            sequenceNo = redisUtils.increment(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId());
        }
        return sequenceNo == null ? 1 : sequenceNo.intValue();
    }

    private int queryDatabaseMaxSequenceNo() {
        if (interviewMessageMapper == null || interviewSession == null || interviewSession.getId() == null) {
            return 0;
        }
        try {
            InterviewMessage message = interviewMessageMapper.selectOne(
                    Wrappers.lambdaQuery(InterviewMessage.class)
                            .select(InterviewMessage::getSequenceNo)
                            .eq(InterviewMessage::getSessionId, interviewSession.getId())
                            .orderByDesc(InterviewMessage::getSequenceNo)
                            .last("limit 1"));
            return message == null || message.getSequenceNo() == null ? 0 : message.getSequenceNo();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void syncRedisMessageSequence(List<InterviewMessage> messages) {
        if (redisUtils == null || interviewSession == null || interviewSession.getId() == null) {
            return;
        }
        int maxSequenceNo = messages.stream()
                .map(InterviewMessage::getSequenceNo)
                .filter(sequenceNo -> sequenceNo != null)
                .max(Integer::compareTo)
                .orElse(0);
        Object current = redisUtils.get(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId());
        long currentValue = current == null ? 0L : Long.parseLong(String.valueOf(current));
        if (currentValue < maxSequenceNo) {
            redisUtils.set(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId(), maxSequenceNo);
        } else {
            redisUtils.expire(RedisKeyEnum.INTERVIEW_MESSAGE_SEQUENCE, interviewSession.getId());
        }
    }

    private CompletableFuture<Void> persistInsert(InterviewMessage message) {
        logInterviewMessagePersist("insert", message);
        CompletableFuture<Void> future;
        if (realtimeMessagePersistService != null) {
            future = realtimeMessagePersistService.insertAsync(message);
        } else {
            interviewMessageMapper.insert(message);
            future = CompletableFuture.completedFuture(null);
        }
        rememberPendingPersistence(future);
        return future;
    }

    private CompletableFuture<Void> persistUpdateAfter(CompletableFuture<Void> previous, InterviewMessage message) {
        logInterviewMessagePersist("update", message);
        CompletableFuture<Void> base = previous == null ? CompletableFuture.completedFuture(null) : previous;
        CompletableFuture<Void> future = base.handle((ignored, throwable) -> null).thenCompose(ignored -> {
            if (message.getSequenceNo() == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (realtimeMessagePersistService != null) {
                return realtimeMessagePersistService.updateAsync(message);
            }
            upsertMessageDirectly(message);
            return CompletableFuture.completedFuture(null);
        });
        rememberPendingPersistence(future);
        return future;
    }

    private void logInterviewMessagePersist(String action, InterviewMessage message) {
        if (message == null) {
            return;
        }
        String content = normalizeMessageContent(message.getContent());
        Map<String, Object> logBody = new LinkedHashMap<>();
        logBody.put("action", action);
        logBody.put("messageId", message.getId());
        logBody.put("role", message.getRole());
        logBody.put("sequenceNo", message.getSequenceNo());
        logBody.put("contentLength", content == null ? 0 : content.length());
        logBody.put("content", content);
        logInterviewProcess("interview_message_persist", logBody);
    }

    private void upsertMessageDirectly(InterviewMessage message) {
        if (message.getId() != null) {
            int updated = interviewMessageMapper.updateById(message);
            if (updated > 0) {
                return;
            }
        }
        InterviewMessage existing = interviewMessageMapper.selectOne(
                Wrappers.lambdaQuery(InterviewMessage.class)
                        .eq(InterviewMessage::getSessionId, message.getSessionId())
                        .eq(InterviewMessage::getSequenceNo, message.getSequenceNo())
                        .last("limit 1"));
        if (existing != null) {
            existing.setRole(message.getRole());
            existing.setContent(message.getContent());
            existing.setAudioUrl(message.getAudioUrl());
            interviewMessageMapper.updateById(existing);
            message.setId(existing.getId());
            return;
        }
        interviewMessageMapper.insert(message);
    }

    private void rememberPendingPersistence(CompletableFuture<Void> future) {
        if (future != null) {
            pendingPersistFutures.add(future);
        }
    }

    public void flushPendingPersistence(Duration timeout) {
        if (pendingPersistFutures.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] futures = pendingPersistFutures.toArray(new CompletableFuture[0]);
        try {
            CompletableFuture.allOf(futures).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            pendingPersistFutures.removeIf(CompletableFuture::isDone);
        }
    }

    private String mergeMessageContent(String current, String incoming) {
        return InterviewMessageUtils.mergeMessageContent(current, incoming);
    }

    private boolean isDuplicateMessage(String role, String content) {
        String last = "CANDIDATE".equals(role) ? lastPersistedCandidateText : lastPersistedAiText;
        return content.equals(last) || (StringUtils.hasText(last) && last.contains(content));
    }

    private void rememberPersistedMessage(String role, String content) {
        if ("CANDIDATE".equals(role)) {
            lastPersistedCandidateText = content;
        } else {
            lastPersistedAiText = content;
        }
    }

    private String normalizeMessageContent(String content) {
        return InterviewMessageUtils.normalizeMessageContent(content);
    }

    private void sendBinary(byte[] bytes) {
        if (volcengineSocket != null) {
            volcengineSocket.sendBinary(ByteBuffer.wrap(bytes));
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void logInterviewProcess(String event, Map<String, Object> extra) {
        if (!INTERVIEW_PROCESS_LOGGER.isInfoEnabled()) {
            return;
        }
        try {
            Map<String, Object> logBody = new LinkedHashMap<>();
            logBody.put("logType", "INTERVIEW_PROCESS");
            logBody.put("event", event);
            logBody.put("sessionId", interviewSession == null ? null : interviewSession.getId());
            logBody.put("jobId", interviewSession == null ? null : interviewSession.getJobId());
            logBody.put("candidateId", interviewSession == null ? null : interviewSession.getCandidateId());
            logBody.put("aiQuestionCount", aiQuestionCount);
            logBody.put("maxQuestionCount", maxQuestionCount());
            logBody.put("closingFollowUpTurnCount", closingFollowUpTurnCount);
            logBody.put("closingFollowUpTurnLimit", closingFollowUpTurnLimit());
            if (extra != null && !extra.isEmpty()) {
                logBody.putAll(extra);
            }
            INTERVIEW_PROCESS_LOGGER.info(objectMapper.writeValueAsString(logBody));
        } catch (Exception ex) {
            INTERVIEW_PROCESS_LOGGER.info("INTERVIEW_PROCESS event={}, sessionId={}", event, interviewSession == null ? null : interviewSession.getId());
        }
    }

    private String limitLogText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void sendBrowserEvent(String event, String message, String payload, int code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("code", code);
        if (message != null) {
            body.put("message", message);
        }
        if (payload != null) {
            body.put("payload", payload);
        }
        sendToBrowser(new TextMessage(toJson(body)));
    }

    private void sendAiTextToBrowser(String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        sendBrowserEvent("chat_response", null, toJson(Map.of(
                "content", content,
                "newMessage", true
        )), 550);
    }

    private String payloadToText(VolcengineTtsProtocol.ParsedMessage message) {
        if (message.getPayload() == null || message.getPayload().length == 0) {
            return "";
        }
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    private String friendlyRealtimeError(String payloadText) {
        if (!StringUtils.hasText(payloadText)) {
            return "实时语音连接异常，请重新连接语音。";
        }
        if (isQuotaExceededError(payloadText)) {
            return "AI 实时语音额度已用完，当前无法继续自动连接。请联系 HR 处理后再继续面试。";
        }
        if (payloadText.contains("52000042") || payloadText.contains("DialogAudioIdleTimeoutError")) {
            return "实时语音长时间没有检测到声音，请点击重新连接语音后直接开始回答。";
        }
        return payloadText;
    }

    private boolean isQuotaExceededError(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("quota")
                || lower.contains("insufficient_quota")
                || lower.contains("quota exceeded")
                || lower.contains("tokens_lifetime")
                || lower.contains("balance")
                || text.contains("余额不足")
                || text.contains("额度不足")
                || text.contains("额度已用完")
                || text.contains("配额不足")
                || text.contains("超出配额");
    }

    private String eventName(int event) {
        return switch (event) {
            case 50 -> "connection_started";
            case 150 -> "session_started";
            case 350 -> "tts_sentence_start";
            case 351 -> "tts_sentence_end";
            case 359 -> "tts_ended";
            case 450 -> "asr_info";
            case 451 -> "asr_response";
            case 459 -> "asr_ended";
            case 550 -> "chat_response";
            case 553 -> "chat_text_query_confirmed";
            case 559 -> "chat_ended";
            case 599 -> "dialog_error";
            default -> "realtime_event";
        };
    }

    private void appendPartialBinary(byte[] bytes) {
        if (partialBinaryMessage == null) {
            partialBinaryMessage = new ByteArrayOutputStream();
        }
        try {
            partialBinaryMessage.write(bytes);
        } catch (Exception ignored) {
        }
    }

    private void sendToBrowser(WebSocketMessage<?> message) {
        try {
            if (browserSession.isOpen()) {
                synchronized (browserSession) {
                    browserSession.sendMessage(message);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
