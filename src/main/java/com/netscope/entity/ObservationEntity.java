package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** One measurement of one device during one scan. This is the historical record behind latency charts. */
@Entity
@Table(name = "observation", indexes = {
        @Index(name = "idx_obs_device_time", columnList = "device_id, observed_at"),
        @Index(name = "idx_obs_time", columnList = "observed_at")})
public class ObservationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "device_id", nullable = false) private Long deviceId;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "observed_at", nullable = false) private Instant observedAt;
    @Column(nullable = false) private boolean reachable;
    @Column(name = "latency_ms") private Double latencyMs;
    private Integer ttl;

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long v) { deviceId = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant v) { observedAt = v; }
    public boolean isReachable() { return reachable; }
    public void setReachable(boolean v) { reachable = v; }
    public Double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Double v) { latencyMs = v; }
    public Integer getTtl() { return ttl; }
    public void setTtl(Integer v) { ttl = v; }
}
