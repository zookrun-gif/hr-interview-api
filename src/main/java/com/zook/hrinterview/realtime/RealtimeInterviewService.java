package com.zook.hrinterview.realtime;

public interface RealtimeInterviewService {

    void startInterview(Long sessionId);

    void receiveCandidateAudio(Long sessionId, byte[] audioChunk);

    void finishInterview(Long sessionId);

    void closeInterview(Long sessionId);
}
