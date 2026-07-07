package com.zook.hrinterview.realtime.socket.impl;

import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocket;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketClient;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@Component
public class OkHttpRealtimeModelWebSocketClient implements RealtimeModelWebSocketClient {

    private final OkHttpClient baseClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    @Override
    public String clientType() {
        return "okhttp";
    }

    @Override
    public RealtimeModelWebSocket connect(
            VolcengineRealtimeProperties properties,
            String connectId,
            RealtimeModelWebSocketListener listener
    ) {
        OkHttpClient client = baseClient.newBuilder()
                .connectTimeout(timeoutSeconds(properties), TimeUnit.SECONDS)
                .build();
        OkHttpRealtimeModelWebSocket.ListenerAdapter adapter = new OkHttpRealtimeModelWebSocket.ListenerAdapter(listener);
        Request request = new Request.Builder()
                .url(properties.getWsUrl())
                .header("X-Api-App-ID", properties.getAppId())
                .header("X-Api-Access-Key", properties.getAccessKey())
                .header("X-Api-Resource-Id", properties.getResourceId())
                .header("X-Api-App-Key", properties.getAppKey())
                .header("X-Api-Connect-Id", connectId)
                .build();
        WebSocket webSocket = client.newWebSocket(request, adapter);
        OkHttpRealtimeModelWebSocket modelWebSocket = new OkHttpRealtimeModelWebSocket(webSocket);
        adapter.setWebSocket(modelWebSocket);
        return modelWebSocket;
    }

    private int timeoutSeconds(VolcengineRealtimeProperties properties) {
        Integer timeoutSeconds = properties.getConnectTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 10 : timeoutSeconds;
    }

    private static class OkHttpRealtimeModelWebSocket implements RealtimeModelWebSocket {

        private final WebSocket webSocket;

        private OkHttpRealtimeModelWebSocket(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            webSocket.send(ByteString.of(bytes));
        }

        @Override
        public void close(String reason) {
            webSocket.close(1000, reason == null ? "" : reason);
        }

        @Override
        public void abort() {
            webSocket.cancel();
        }

        private static class ListenerAdapter extends WebSocketListener {

            private final RealtimeModelWebSocketListener delegate;

            private OkHttpRealtimeModelWebSocket webSocket;

            private ListenerAdapter(RealtimeModelWebSocketListener delegate) {
                this.delegate = delegate;
            }

            private void setWebSocket(OkHttpRealtimeModelWebSocket webSocket) {
                this.webSocket = webSocket;
            }

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                delegate.onOpen(this.webSocket);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                delegate.onText(text);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                delegate.onBinary(bytes.toByteArray(), true);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                delegate.onClose(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                delegate.onClose(code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                delegate.onError(t);
            }
        }
    }
}
