package com.zook.hrinterview.realtime.socket.impl;

import com.zook.hrinterview.config.VolcengineRealtimeProperties;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocket;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketClient;
import com.zook.hrinterview.realtime.socket.RealtimeModelWebSocketListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Component
public class JavaRealtimeModelWebSocketClient implements RealtimeModelWebSocketClient {

    @Override
    public String clientType() {
        return "java";
    }

    @Override
    public RealtimeModelWebSocket connect(
            VolcengineRealtimeProperties properties,
            String connectId,
            RealtimeModelWebSocketListener listener
    ) {
        JavaRealtimeModelWebSocket.ListenerAdapter adapter = new JavaRealtimeModelWebSocket.ListenerAdapter(listener);
        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds(properties)))
                .header("X-Api-App-ID", properties.getAppId())
                .header("X-Api-Access-Key", properties.getAccessKey())
                .header("X-Api-Resource-Id", properties.getResourceId())
                .header("X-Api-App-Key", properties.getAppKey())
                .header("X-Api-Connect-Id", connectId)
                .buildAsync(URI.create(properties.getWsUrl()), adapter)
                .join();
        adapter.setWebSocket(new JavaRealtimeModelWebSocket(webSocket));
        return adapter.getWebSocket();
    }

    private int timeoutSeconds(VolcengineRealtimeProperties properties) {
        Integer timeoutSeconds = properties.getConnectTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 10 : timeoutSeconds;
    }

    private static class JavaRealtimeModelWebSocket implements RealtimeModelWebSocket {

        private final WebSocket webSocket;

        private JavaRealtimeModelWebSocket(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            webSocket.sendBinary(data, true);
        }

        @Override
        public void close(String reason) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason == null ? "" : reason);
        }

        @Override
        public void abort() {
            webSocket.abort();
        }

        private static class ListenerAdapter implements WebSocket.Listener {

            private final RealtimeModelWebSocketListener delegate;

            private JavaRealtimeModelWebSocket webSocket;

            private ListenerAdapter(RealtimeModelWebSocketListener delegate) {
                this.delegate = delegate;
            }

            private JavaRealtimeModelWebSocket getWebSocket() {
                return webSocket;
            }

            private void setWebSocket(JavaRealtimeModelWebSocket webSocket) {
                this.webSocket = webSocket;
            }

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                delegate.onOpen(this.webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                delegate.onText(data.toString());
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                delegate.onBinary(bytes, last);
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                delegate.onClose(statusCode, reason);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                delegate.onError(error);
            }
        }
    }
}
