package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "scan_session")
public class ScanSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scan_mode", length = 12) private String scanMode;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(length = 12) private String status;
    @Column(name = "targets_probed") private Integer targetsProbed;
    @Column(name = "devices_reachable") private Integer devicesReachable;
    @Column(name = "devices_total") private Integer devicesTotal;
    @Column(length = 500) private String scopes;
    @Column(length = 500) private String error;

    public Long getId() { return id; }
    public String getScanMode() { return scanMode; }
    public void setScanMode(String v) { scanMode = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { finishedAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Integer getTargetsProbed() { return targetsProbed; }
    public void setTargetsProbed(Integer v) { targetsProbed = v; }
    public Integer getDevicesReachable() { return devicesReachable; }
    public void setDevicesReachable(Integer v) { devicesReachable = v; }
    public Integer getDevicesTotal() { return devicesTotal; }
    public void setDevicesTotal(Integer v) { devicesTotal = v; }
    public String getScopes() { return scopes; }
    public void setScopes(String v) { scopes = v; }
    public String getError() { return error; }
    public void setError(String v) { error = v; }
}
