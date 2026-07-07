package com.zook.hrinterview.realtime.socket;

import java.nio.ByteBuffer;

public interface RealtimeModelWebSocket {

    void sendBinary(ByteBuffer data);

    void close(String reason);

    void abort();
}
