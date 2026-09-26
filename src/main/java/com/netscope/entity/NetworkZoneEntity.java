package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "network_zone", indexes = @Index(name = "idx_zone_cidr", columnList = "cidr", unique = true))
public class NetworkZoneEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 24) private String cidr;
    @Column(length = 300) private String description;
    @Column(name = "zone_type", nullable = false, length = 16) private String zoneType;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private boolean authorized = false;
    @Column(name = "authorized_by", length = 120) private String authorizedBy;
    @Column(length = 45) private String gateway;
    @Column(length = 20) private String methods = "ICMP,TCP";
    @Column(name = "tcp_ports", length = 60) private String tcpPorts = "80,443,22";
    @Column(name = "use_nmap", nullable = false) private boolean useNmap = true;
    @Column(name = "max_concurrency", nullable = false) private int maxConcurrency = 20;
    @Column(name = "timeout_ms", nullable = false) private int timeoutMs = 1000;
    @Column(name = "max_devices", nullable = false) private int maxDevices = 512;
    @Column(name = "interval_seconds", nullable = false) private int intervalSeconds = 60;
    @Column(name = "failure_threshold", nullable = false) private int failureThreshold = 3;
    @Column(nullable = false, length = 16) private String status = "IDLE";
    @Column(name = "last_scan_at") private Instant lastScanAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getCidr() { return cidr; }
    public void setCidr(String v) { cidr = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getZoneType() { return zoneType; }
    public void setZoneType(String v) { zoneType = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean v) { authorized = v; }
    public String getAuthorizedBy() { return authorizedBy; }
    public void setAuthorizedBy(String v) { authorizedBy = v; }
    public String getGateway() { return gateway; }
    public void setGateway(String v) { gateway = v; }
    public String getMethods() { return methods; }
    public void setMethods(String v) { methods = v; }
    public String getTcpPorts() { return tcpPorts; }
    public void setTcpPorts(String v) { tcpPorts = v; }
    public boolean isUseNmap() { return useNmap; }
    public void setUseNmap(boolean v) { useNmap = v; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int v) { maxConcurrency = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { timeoutMs = v; }
    public int getMaxDevices() { return maxDevices; }
    public void setMaxDevices(int v) { maxDevices = v; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int v) { intervalSeconds = v; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int v) { failureThreshold = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getLastScanAt() { return lastScanAt; }
    public void setLastScanAt(Instant v) { lastScanAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
