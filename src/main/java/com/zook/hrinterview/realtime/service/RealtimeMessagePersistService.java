package com.zook.hrinterview.realtime.service;

import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RealtimeMessagePersistService {

    CompletableFuture<Void> insertAsync(InterviewMessage message);

    CompletableFuture<Void> updateAsync(InterviewMessage message);

    List<InterviewMessage> listBufferedMessages(Long sessionId);
}
