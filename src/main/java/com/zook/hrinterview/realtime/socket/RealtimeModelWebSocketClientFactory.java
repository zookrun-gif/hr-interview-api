package com.zook.hrinterview.realtime.socket;

import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RealtimeModelWebSocketClientFactory {

    private final List<RealtimeModelWebSocketClient> clients;

    public RealtimeModelWebSocketClientFactory(List<RealtimeModelWebSocketClient> clients) {
        this.clients = clients;
    }

    public RealtimeModelWebSocketClient select(VolcengineRealtimeProperties properties) {
        String clientType = properties.getWebsocketClient() == null ? "java" : properties.getWebsocketClient();
        String normalized = clientType.toLowerCase(Locale.ROOT).trim();
        return clients.stream()
                .filter(client -> client.clientType().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseGet(() -> clients.stream()
                        .filter(client -> "java".equalsIgnoreCase(client.clientType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Realtime WebSocket client not found")));
    }
}
