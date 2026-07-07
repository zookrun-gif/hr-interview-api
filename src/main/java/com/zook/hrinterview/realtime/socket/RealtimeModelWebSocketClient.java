package com.zook.hrinterview.realtime.socket;

import com.zook.hrinterview.config.VolcengineRealtimeProperties;

public interface RealtimeModelWebSocketClient {

    String clientType();

    RealtimeModelWebSocket connect(
            VolcengineRealtimeProperties properties,
            String connectId,
            RealtimeModelWebSocketListener listener
    );
}
