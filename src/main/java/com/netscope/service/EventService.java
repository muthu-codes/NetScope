package com.netscope.service;

import com.netscope.model.NetworkEvent;
import com.netscope.websocket.NetworkEventWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Creates events, keeps the newest 1000 in memory, stores them in the DB and pushes them over WebSocket. */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final int MAX_IN_MEMORY = 1000;

    private final PersistenceService persistence;
    private final NetworkEventWebSocketHandler ws;
    private final Deque<NetworkEvent> buffer = new ConcurrentLinkedDeque<>();     // newest first
    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis());

    public EventService(PersistenceService persistence, NetworkEventWebSocketHandler ws) {
        this.persistence = persistence;
        this.ws = ws;
    }

    @PostConstruct
    void restore() {
        List<NetworkEvent> saved = new ArrayList<>(persistence.loadRecentEvents());   // newest first
        Collections.reverse(saved);                                                    // oldest first
        for (NetworkEvent e : saved) buffer.addFirst(e);
        log.info("Restored {} events from history", buffer.size());
    }

    public NetworkEvent emit(String type, String severity, String ip, String mac, String hostname, String message) {
        NetworkEvent ev = new NetworkEvent(ids.incrementAndGet(), Instant.now(), type, severity, ip, mac, hostname, message);
        buffer.addFirst(ev);
        while (buffer.size() > MAX_IN_MEMORY) buffer.pollLast();
        persistence.saveEvent(ev);
        ws.broadcast("EVENT", ev);
        log.info("EVENT {} [{}] {}", type, severity, message);
        return ev;
    }

    /** Newest first, optionally filtered by type and/or severity. */
    public List<NetworkEvent> latest(int limit, String type, String severity) {
        int max = Math.max(1, Math.min(limit, MAX_IN_MEMORY));
        List<NetworkEvent> out = new ArrayList<>();
        for (NetworkEvent e : buffer) {
            if (type != null && !type.isBlank() && !e.type().equalsIgnoreCase(type)) continue;
            if (severity != null && !severity.isBlank() && !e.severity().toUpperCase(Locale.ROOT).equals(severity.toUpperCase(Locale.ROOT))) continue;
            out.add(e);
            if (out.size() >= max) break;
        }
        return out;
    }
}
