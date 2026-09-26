package com.netscope.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes live messages to every open dashboard: {"type": "...", "at": "...", "payload": {...}}
 * Types: HELLO, EVENT, SCAN_PROGRESS, SCAN_COMPLETE.
 */
@Component
public class NetworkEventWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NetworkEventWebSocketHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public NetworkEventWebSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024));
        send(sessions.get(session.getId()), "HELLO", Map.of("message", "NetScope live feed connected"));
        log.debug("WebSocket connected: {} (total {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
    }

    /** Sends one message to all connected dashboards. Never throws. */
    public void broadcast(String type, Object payload) {
        if (sessions.isEmpty()) return;
        for (WebSocketSession s : sessions.values()) send(s, type, payload);
    }

    private void send(WebSocketSession session, String type, Object payload) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", type);
            msg.put("at", Instant.now().toString());
            msg.put("payload", payload);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            sessions.remove(session.getId());
            log.debug("Dropped WebSocket session {}: {}", session.getId(), e.getMessage());
        }
    }
}
