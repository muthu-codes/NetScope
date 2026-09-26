package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "network_event", indexes = @Index(name = "idx_event_time", columnList = "occurred_at"))
public class NetworkEventEntity {
    @Id private Long id;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(length = 12) private String severity;
    @Column(length = 45) private String ip;
    @Column(length = 17) private String mac;
    @Column(length = 255) private String hostname;
    @Column(length = 700) private String message;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { occurredAt = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { eventType = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { severity = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { ip = v; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { hostname = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
}
