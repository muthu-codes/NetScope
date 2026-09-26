package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/** A device discovered inside a routed/server zone (distinct from the local-subnet DeviceEntity/ARP table). */
@Entity
@Table(name = "zone_device", indexes = {
        @Index(name = "idx_zone_device_zone", columnList = "zone_id"),
        @Index(name = "idx_zone_device_ip", columnList = "ip") })
public class ZoneDeviceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "zone_id", nullable = false) private Long zoneId;
    @Column(nullable = false, length = 45) private String ip;
    @Column(length = 17) private String mac;
    @Column(length = 255) private String hostname;
    @Column(nullable = false, length = 12) private String state = "UNKNOWN";
    @Column(name = "discovery_method", length = 12) private String discoveryMethod;
    @Column(name = "latency_ms") private Double latencyMs;
    @Column(name = "packet_loss_percent") private Double packetLossPercent;
    @Column(name = "consecutive_failures", nullable = false) private int consecutiveFailures;
    @Column(name = "open_ports", length = 200) private String openPorts;
    @Column(name = "first_seen") private Instant firstSeen;
    @Column(name = "last_seen") private Instant lastSeen;
    @Transient private List<Double> latencyHistory;

    @PrePersist
    void onCreate() {
        if (firstSeen == null) firstSeen = Instant.now();
    }

    public Long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long v) { zoneId = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { ip = v; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { hostname = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public String getDiscoveryMethod() { return discoveryMethod; }
    public void setDiscoveryMethod(String v) { discoveryMethod = v; }
    public Double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Double v) { latencyMs = v; }
    public Double getPacketLossPercent() { return packetLossPercent; }
    public void setPacketLossPercent(Double v) { packetLossPercent = v; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int v) { consecutiveFailures = v; }
    public String getOpenPorts() { return openPorts; }
    public void setOpenPorts(String v) { openPorts = v; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant v) { firstSeen = v; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant v) { lastSeen = v; }
}
