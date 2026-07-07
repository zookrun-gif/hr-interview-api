package com.zook.hrinterview.config;

import com.zook.hrinterview.realtime.handler.VolcengineRealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private VolcengineRealtimeWebSocketHandler volcengineRealtimeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(volcengineRealtimeWebSocketHandler, "/ws/public/interviews/realtime")
                .setAllowedOriginPatterns("*");
    }
}
