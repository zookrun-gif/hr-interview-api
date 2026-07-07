package com.zook.hrinterview.realtime.event;

import lombok.Getter;

@Getter
public class InterviewAutoFinishEvent {

    private final Long sessionId;

    private final String reason;

    public InterviewAutoFinishEvent(Long sessionId, String reason) {
        this.sessionId = sessionId;
        this.reason = reason;
    }
}
