package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "device", indexes = @Index(name = "idx_device_ip", columnList = "ip", unique = true))
public class DeviceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 45) private String ip;
    @Column(length = 17) private String mac;
    @Column(length = 255) private String hostname;
    @Column(length = 120) private String vendor;
    @Column(name = "device_type", length = 30) private String deviceType;
    @Column(length = 20) private String state;
    @Column(length = 24) private String subnet;
    @Column(name = "first_seen") private Instant firstSeen;
    @Column(name = "last_seen") private Instant lastSeen;
    @Column(name = "last_latency_ms") private Double lastLatencyMs;

    public Long getId() { return id; }
    public String getIp() { return ip; }
    public void setIp(String v) { ip = v; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { hostname = v; }
    public String getVendor() { return vendor; }
    public void setVendor(String v) { vendor = v; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String v) { deviceType = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public String getSubnet() { return subnet; }
    public void setSubnet(String v) { subnet = v; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant v) { firstSeen = v; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant v) { lastSeen = v; }
    public Double getLastLatencyMs() { return lastLatencyMs; }
    public void setLastLatencyMs(Double v) { lastLatencyMs = v; }
}
