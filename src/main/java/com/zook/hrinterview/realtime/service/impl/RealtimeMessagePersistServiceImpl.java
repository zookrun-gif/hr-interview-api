package com.zook.hrinterview.realtime.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewMessageMapper;
import com.zook.hrinterview.realtime.dto.RealtimeMessagePersistRetryItem;
import com.zook.hrinterview.realtime.service.RealtimeMessagePersistService;
import com.zook.hrinterview.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class RealtimeMessagePersistServiceImpl implements RealtimeMessagePersistService {

    private static final String OPERATION_UPSERT = "UPSERT";

    private static final int RETRY_BATCH_SIZE = 50;

    private static final int MAX_RETRY_COUNT = 20;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private InterviewMessageMapper interviewMessageMapper;

    @Resource
    private RedisUtils redisUtils;

    @PostConstruct
    public void recoverProcessingQueueOnStartup() {
        recoverProcessingQueue();
    }

    @Override
    public CompletableFuture<Void> insertAsync(InterviewMessage message) {
        if (message == null || message.getSessionId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        enqueue(message, null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> updateAsync(InterviewMessage message) {
        if (message == null || message.getSessionId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        enqueue(message, null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<InterviewMessage> listBufferedMessages(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        Map<Object, Object> values = redisUtils.hGetAll(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER.buildKey(sessionId));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<InterviewMessage> messages = new ArrayList<>();
        for (Object value : values.values()) {
            RealtimeMessagePersistRetryItem item = toRetryItem(value);
            if (item == null || item.getSessionId() == null || !sessionId.equals(item.getSessionId())) {
                continue;
            }
            messages.add(toMessage(item));
        }
        messages.sort(Comparator.comparing(InterviewMessage::getSequenceNo, Comparator.nullsLast(Integer::compareTo)));
        return messages;
    }

    @Scheduled(fixedDelayString = "${interview.message.persist-retry.poll-interval-millis:3000}")
    public void retryPersistFailedMessages() {
        for (int i = 0; i < RETRY_BATCH_SIZE; i++) {
            Object rawItem = redisUtils.lRightPopAndLeftPush(
                    RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PENDING_QUEUE,
                    RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE
            );
            if (rawItem == null) {
                return;
            }
            RealtimeMessagePersistRetryItem item = toRetryItem(rawItem);
            if (item == null) {
                redisUtils.lRemove(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE, 1, rawItem);
                continue;
            }
            try {
                upsert(toMessage(item));
                removeSessionBufferIfSame(item);
                redisUtils.lRemove(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE, 1, rawItem);
                log.info("Realtime message retry persisted, sessionId={}, sequenceNo={}, retry={}",
                        item.getSessionId(), item.getSequenceNo(), item.getRetryCount());
            } catch (Exception ex) {
                redisUtils.lRemove(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE, 1, rawItem);
                requeueOrDeadLetter(item, ex);
            }
        }
    }

    private void recoverProcessingQueue() {
        for (int i = 0; i < RETRY_BATCH_SIZE; i++) {
            Object rawItem = redisUtils.lRightPopAndLeftPush(
                    RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE,
                    RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PENDING_QUEUE
            );
            if (rawItem == null) {
                return;
            }
        }
    }

    private void upsert(InterviewMessage message) {
        if (message == null || message.getSessionId() == null || message.getSequenceNo() == null) {
            return;
        }
        if (message.getId() != null) {
            InterviewMessage existingById = interviewMessageMapper.selectById(message.getId());
            if (existingById != null) {
                applyLatestContent(existingById, message);
                interviewMessageMapper.updateById(existingById);
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
            applyLatestContent(existing, message);
            existing.setAudioUrl(message.getAudioUrl());
            interviewMessageMapper.updateById(existing);
            message.setId(existing.getId());
            return;
        }
        InterviewMessage insertMessage = copyForInsert(message);
        interviewMessageMapper.insert(insertMessage);
        message.setId(insertMessage.getId());
    }

    private void applyLatestContent(InterviewMessage existing, InterviewMessage incoming) {
        existing.setRole(incoming.getRole());
        existing.setAudioUrl(incoming.getAudioUrl());
        String existingContent = existing.getContent();
        String incomingContent = incoming.getContent();
        if (incomingContent == null) {
            return;
        }
        if (existingContent == null || incomingContent.length() >= existingContent.length()) {
            existing.setContent(incomingContent);
        }
    }

    private InterviewMessage copyForInsert(InterviewMessage source) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(source.getSessionId());
        message.setRole(source.getRole());
        message.setContent(source.getContent());
        message.setAudioUrl(source.getAudioUrl());
        message.setSequenceNo(source.getSequenceNo());
        return message;
    }

    private void enqueue(InterviewMessage message, Exception ex) {
        RealtimeMessagePersistRetryItem item = toQueueItem(message, ex, 0);
        try {
            writeSessionBuffer(item);
            redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PENDING_QUEUE, item);
            log.debug("Realtime message queued for async persist, sessionId={}, sequenceNo={}",
                    item.getSessionId(), item.getSequenceNo());
        } catch (Exception redisEx) {
            try {
                upsert(message);
                removeSessionBufferIfSame(item);
                log.warn("Realtime message queue unavailable, fallback to sync db persist, sessionId={}, sequenceNo={}",
                        item.getSessionId(), item.getSequenceNo(), redisEx);
            } catch (Exception dbEx) {
                log.error("Realtime message persist failed because both redis queue and db fallback are unavailable, sessionId={}, sequenceNo={}",
                        item.getSessionId(), item.getSequenceNo(), dbEx);
            }
        }
    }

    private void requeueOrDeadLetter(RealtimeMessagePersistRetryItem item, Exception ex) {
        int retryCount = item.getRetryCount() == null ? 1 : item.getRetryCount() + 1;
        item.setRetryCount(retryCount);
        item.setLastError(ex.getMessage());
        if (retryCount <= MAX_RETRY_COUNT) {
            redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_PENDING_QUEUE, item);
            log.warn("Realtime message retry failed and requeued, sessionId={}, sequenceNo={}, retry={}/{}",
                    item.getSessionId(), item.getSequenceNo(), retryCount, MAX_RETRY_COUNT, ex);
            return;
        }
        redisUtils.lLeftPush(RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_FAILED_QUEUE, item);
        log.warn("Realtime message retry failed permanently, moved to dead letter queue, sessionId={}, sequenceNo={}, retry={}",
                item.getSessionId(), item.getSequenceNo(), retryCount, ex);
    }

    private void writeSessionBuffer(RealtimeMessagePersistRetryItem item) {
        if (item.getSessionId() == null || item.getSequenceNo() == null) {
            return;
        }
        String key = RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER.buildKey(item.getSessionId());
        redisUtils.hSet(key, String.valueOf(item.getSequenceNo()), item);
        redisUtils.expire(key, RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER.getTtl());
    }

    private void removeSessionBufferIfSame(RealtimeMessagePersistRetryItem item) {
        if (item.getSessionId() == null || item.getSequenceNo() == null) {
            return;
        }
        String key = RedisKeyEnum.INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER.buildKey(item.getSessionId());
        String hashKey = String.valueOf(item.getSequenceNo());
        RealtimeMessagePersistRetryItem latest = toRetryItem(redisUtils.hGet(key, hashKey));
        if (latest == null || latest.getContent() == null || item.getContent() == null) {
            return;
        }
        if (latest.getContent().equals(item.getContent())) {
            redisUtils.hDelete(key, hashKey);
        }
    }

    private RealtimeMessagePersistRetryItem toQueueItem(InterviewMessage message, Exception ex, int retryCount) {
        RealtimeMessagePersistRetryItem item = new RealtimeMessagePersistRetryItem();
        item.setOperation(OPERATION_UPSERT);
        item.setId(message.getId());
        item.setSessionId(message.getSessionId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setAudioUrl(message.getAudioUrl());
        item.setSequenceNo(message.getSequenceNo());
        item.setCreatedAt(format(message.getCreatedAt()));
        item.setRetryCount(retryCount);
        item.setLastError(ex == null ? null : ex.getMessage());
        return item;
    }

    @SuppressWarnings("unchecked")
    private RealtimeMessagePersistRetryItem toRetryItem(Object rawItem) {
        if (rawItem == null) {
            return null;
        }
        if (rawItem instanceof RealtimeMessagePersistRetryItem) {
            return (RealtimeMessagePersistRetryItem) rawItem;
        }
        if (!(rawItem instanceof Map)) {
            log.warn("Invalid realtime message retry item type={}", rawItem.getClass().getName());
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) rawItem;
        RealtimeMessagePersistRetryItem item = new RealtimeMessagePersistRetryItem();
        item.setOperation(stringValue(map.get("operation")));
        item.setId(longValue(map.get("id")));
        item.setSessionId(longValue(map.get("sessionId")));
        item.setRole(stringValue(map.get("role")));
        item.setContent(stringValue(map.get("content")));
        item.setAudioUrl(stringValue(map.get("audioUrl")));
        item.setSequenceNo(intValue(map.get("sequenceNo")));
        item.setCreatedAt(stringValue(map.get("createdAt")));
        item.setRetryCount(intValue(map.get("retryCount")));
        item.setLastError(stringValue(map.get("lastError")));
        return item;
    }

    private InterviewMessage toMessage(RealtimeMessagePersistRetryItem item) {
        InterviewMessage message = new InterviewMessage();
        message.setId(item.getId());
        message.setSessionId(item.getSessionId());
        message.setRole(item.getRole());
        message.setContent(item.getContent());
        message.setAudioUrl(item.getAudioUrl());
        message.setSequenceNo(item.getSequenceNo());
        return message;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
