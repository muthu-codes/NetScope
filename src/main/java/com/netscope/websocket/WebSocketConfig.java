package com.netscope.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NetworkEventWebSocketHandler handler;
    private final String[] origins;

    public WebSocketConfig(NetworkEventWebSocketHandler handler,
                           @Value("${netscope.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String[] origins) {
        this.handler = handler;
        this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same-origin (production build served by Spring) is always allowed; the dev server origins come from config.
        registry.addHandler(handler, "/ws/events").setAllowedOrigins(origins);
    }
}
