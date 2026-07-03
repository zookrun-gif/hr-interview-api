package com.zook.hrinterview.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class VolcengineRealtimeSession implements WebSocket.Listener {

    private final VolcengineRealtimeProperties properties;

    private final ObjectMapper objectMapper;

    private final WebSocketSession browserSession;

    private final InterviewSession interviewSession;

    private final JobPosition job;

    private final Candidate candidate;

    private final InterviewMessageMapper interviewMessageMapper;

    private final String realtimeSessionId = UUID.randomUUID().toString();

    private final String connectId = UUID.randomUUID().toString();

    private WebSocket volcengineSocket;

    private ByteArrayOutputStream partialBinaryMessage;

    private String lastPersistedCandidateText = "";

    private String lastPersistedAiText = "";

    private Long currentPersistedCandidateMessageId;

    private Long currentPersistedAiMessageId;

    private boolean hasAiChatResponseInTurn;

    public VolcengineRealtimeSession(
            VolcengineRealtimeProperties properties,
            ObjectMapper objectMapper,
            WebSocketSession browserSession,
            InterviewSession interviewSession,
            JobPosition job,
            Candidate candidate,
            InterviewMessageMapper interviewMessageMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.browserSession = browserSession;
        this.interviewSession = interviewSession;
        this.job = job;
        this.candidate = candidate;
        this.interviewMessageMapper = interviewMessageMapper;
    }

    public void connect() {
        this.volcengineSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("X-Api-App-ID", properties.getAppId())
                .header("X-Api-Access-Key", properties.getAccessKey())
                .header("X-Api-Resource-Id", properties.getResourceId())
                .header("X-Api-App-Key", properties.getAppKey())
                .header("X-Api-Connect-Id", connectId)
                .buildAsync(URI.create(properties.getWsUrl()), this)
                .join();
        sendBinary(VolcengineTtsProtocol.startConnection());
        sendStartSession();
        sayHello(buildOpeningText());
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        appendMessage("CANDIDATE", text);
        sendBinary(VolcengineTtsProtocol.chatTextQuery(realtimeSessionId, toJson(payload)));
    }

    public void sayHello(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        appendMessage("AI", text);
        sendBinary(VolcengineTtsProtocol.sayHello(realtimeSessionId, toJson(payload)));
    }

    public void finish() {
        if (volcengineSocket != null) {
            sendBinary(VolcengineTtsProtocol.finishSession(realtimeSessionId));
            sendBinary(VolcengineTtsProtocol.finishConnection());
            volcengineSocket.sendClose(WebSocket.NORMAL_CLOSURE, "finish");
        }
    }

    public void close() {
        if (volcengineSocket != null) {
            volcengineSocket.abort();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        sendToBrowser(new TextMessage(data.toString()));
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        if (last && partialBinaryMessage == null) {
            handleVolcengineMessage(bytes);
        } else {
            appendPartialBinary(bytes);
            if (last) {
                handleVolcengineMessage(partialBinaryMessage.toByteArray());
                partialBinaryMessage = null;
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        sendBrowserEvent("disconnected", "Realtime 模型连接已关闭：" + statusCode + (StringUtils.hasText(reason) ? "，" + reason : ""), null, statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
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
            dialog.put("speaking_style", "中文口语表达，简洁、清晰、有礼貌。每次只问一个问题，不要连续追问多个问题。");
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
        builder.append("你好，我是本次 AI 面试官。");
        if (job != null) {
            builder.append("本次面试岗位是").append(nullToEmpty(job.getTitle())).append("。");
        }
        builder.append("请先用一分钟做一个自我介绍。");
        return builder.toString();
    }

    private String buildCharacterManifest() {
        StringBuilder builder = new StringBuilder();
        builder.append("name: AI面试官\n");
        builder.append("role: |\n");
        appendYamlBlock(builder, "你是一名专业、友善、克制的 AI 面试官。");
        builder.append("speaking_style: |\n");
        appendYamlBlock(builder, "中文口语表达，简洁、清晰、有礼貌。每次只问一个问题，不要连续追问多个问题。");
        builder.append("interview_rules: |\n");
        appendYamlBlock(builder, nullToEmpty(properties.getInstructions()));
        if (job != null) {
            builder.append("job_title: |\n");
            appendYamlBlock(builder, nullToEmpty(job.getTitle()));
            builder.append("job_description: |\n");
            appendYamlBlock(builder, limit(job.getJd(), 1200));
            builder.append("requirements: |\n");
            appendYamlBlock(builder, limit(job.getRequirements(), 1200));
        }
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
        return builder.toString();
    }

    private String buildSystemRole() {
        StringBuilder builder = new StringBuilder();
        builder.append(nullToEmpty(properties.getInstructions()));
        if (job != null) {
            builder.append("\n\n岗位名称：").append(sanitizeManifestText(job.getTitle()));
            builder.append("\n岗位JD：").append(sanitizeManifestText(limit(job.getJd(), 1200)));
            builder.append("\n能力要求：").append(sanitizeManifestText(limit(job.getRequirements(), 1200)));
        }
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
        return builder.toString();
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
                sendAudioToBrowser(message.getPayload());
                return;
            }
            if (message.getMsgType() == VolcengineTtsProtocol.MSG_TYPE_ERROR) {
                sendBrowserEvent("error", payloadToText(message), null, message.getEvent());
                return;
            }
            if (message.getEvent() == 352 && message.getPayload() != null && message.getPayload().length > 0) {
                sendAudioToBrowser(message.getPayload());
                return;
            }
            if (message.getPayload() != null && message.getPayload().length > 0) {
                String payloadText = payloadToText(message);
                appendRealtimeTextMessage(message.getEvent(), payloadText);
                sendBrowserEvent(eventName(message.getEvent()), null, payloadText, message.getEvent());
            }
        } catch (Exception ex) {
            sendBrowserEvent("error", "火山 Realtime 响应解析失败", null, 0);
        }
    }

    private void sendAudioToBrowser(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        sendToBrowser(new TextMessage("audio:" + Base64.getEncoder().encodeToString(payload)));
    }

    private void appendRealtimeTextMessage(int event, String payloadText) {
        if (!StringUtils.hasText(payloadText)) {
            return;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(payloadText, Map.class);
            if (event == 451) {
                String text = extractAsrText(payload);
                if (StringUtils.hasText(text)) {
                    resetAiPersistTurn();
                    appendOrUpdateCandidateMessage(text);
                }
                return;
            }
            if (event == 550) {
                Object content = payload.get("content");
                if (content == null) {
                    content = payload.get("text");
                }
                if (content != null && StringUtils.hasText(String.valueOf(content))) {
                    hasAiChatResponseInTurn = true;
                    appendOrUpdateAiMessage(String.valueOf(content));
                }
                return;
            }
            if (event == 350 && !hasAiChatResponseInTurn) {
                Object content = payload.get("content");
                if (content == null) {
                    content = payload.get("text");
                }
                if (content != null && StringUtils.hasText(String.valueOf(content))) {
                    appendOrUpdateAiMessage(String.valueOf(content));
                }
            }
        } catch (Exception ignored) {
        }
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
            Long count = interviewMessageMapper.selectCount(
                    Wrappers.lambdaQuery(InterviewMessage.class)
                            .eq(InterviewMessage::getSessionId, interviewSession.getId()));
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole(role);
            message.setContent(normalized);
            message.setSequenceNo(count.intValue() + 1);
            interviewMessageMapper.insert(message);
            rememberPersistedMessage(role, normalized);
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
            if (currentPersistedCandidateMessageId != null) {
                InterviewMessage existing = interviewMessageMapper.selectById(currentPersistedCandidateMessageId);
                if (existing != null) {
                    String merged = mergeMessageContent(existing.getContent(), normalized);
                    if (StringUtils.hasText(merged) && !merged.equals(existing.getContent())) {
                        existing.setContent(merged);
                        interviewMessageMapper.updateById(existing);
                        rememberPersistedMessage("CANDIDATE", merged);
                    }
                    return;
                }
            }
            if (isDuplicateMessage("CANDIDATE", normalized)) {
                return;
            }
            Long count = interviewMessageMapper.selectCount(
                    Wrappers.lambdaQuery(InterviewMessage.class)
                            .eq(InterviewMessage::getSessionId, interviewSession.getId()));
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole("CANDIDATE");
            message.setContent(normalized);
            message.setSequenceNo(count.intValue() + 1);
            interviewMessageMapper.insert(message);
            currentPersistedCandidateMessageId = message.getId();
            rememberPersistedMessage("CANDIDATE", normalized);
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
            currentPersistedCandidateMessageId = null;
            if (currentPersistedAiMessageId != null) {
                InterviewMessage existing = interviewMessageMapper.selectById(currentPersistedAiMessageId);
                if (existing != null) {
                    String merged = mergeMessageContent(existing.getContent(), normalized);
                    if (StringUtils.hasText(merged) && !merged.equals(existing.getContent())) {
                        existing.setContent(merged);
                        interviewMessageMapper.updateById(existing);
                        rememberPersistedMessage("AI", merged);
                    }
                    return;
                }
            }
            if (isDuplicateMessage("AI", normalized)) {
                return;
            }
            Long count = interviewMessageMapper.selectCount(
                    Wrappers.lambdaQuery(InterviewMessage.class)
                            .eq(InterviewMessage::getSessionId, interviewSession.getId()));
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(interviewSession.getId());
            message.setRole("AI");
            message.setContent(normalized);
            message.setSequenceNo(count.intValue() + 1);
            interviewMessageMapper.insert(message);
            currentPersistedAiMessageId = message.getId();
            rememberPersistedMessage("AI", normalized);
        } catch (Exception ignored) {
        }
    }

    private void resetAiPersistTurn() {
        currentPersistedAiMessageId = null;
        hasAiChatResponseInTurn = false;
    }

    private String mergeMessageContent(String current, String incoming) {
        String base = normalizeMessageContent(current);
        String next = normalizeMessageContent(incoming);
        if (!StringUtils.hasText(base)) {
            return next;
        }
        if (!StringUtils.hasText(next) || base.equals(next) || base.endsWith(next)) {
            return base;
        }
        if (next.startsWith(base)) {
            return next;
        }
        return base + (needsSpaceBetween(base, next) ? " " : "") + next;
    }

    private boolean needsSpaceBetween(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        char last = left.charAt(left.length() - 1);
        char first = right.charAt(0);
        return Character.isLetterOrDigit(last) && Character.isLetterOrDigit(first)
                && last < 128 && first < 128;
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
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private void sendBinary(byte[] bytes) {
        if (volcengineSocket != null) {
            volcengineSocket.sendBinary(ByteBuffer.wrap(bytes), true);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
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

    private String payloadToText(VolcengineTtsProtocol.ParsedMessage message) {
        if (message.getPayload() == null || message.getPayload().length == 0) {
            return "";
        }
        return new String(message.getPayload(), StandardCharsets.UTF_8);
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
