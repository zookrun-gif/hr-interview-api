package com.zook.hrinterview.realtime.socket;

public interface RealtimeModelWebSocketListener {

    void onOpen(RealtimeModelWebSocket webSocket);

    void onText(String text);

    void onBinary(byte[] bytes, boolean last);

    void onClose(int statusCode, String reason);

    void onError(Throwable error);
}
