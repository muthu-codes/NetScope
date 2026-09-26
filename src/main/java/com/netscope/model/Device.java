package com.netscope.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Live, mutable state of one observed device (one IPv4 address).
 * Only DeviceDiscoveryService changes it; everything else works with immutable DeviceResponse copies.
 */
public class Device {
    private final String ip;
    private String mac, hostname, hostnameSource, vendor, vendorNote, classificationReason, osHint, subnet, source;
    private String deviceType = "UNKNOWN";
    private String state = "UNKNOWN";           // REACHABLE, ARP_ONLY, UNREACHABLE, UNKNOWN
    private String latencyBand;                 // OK, HIGH, CRITICAL
    private String healthStatus = "UNKNOWN";    // HEALTHY, DEGRADED, CRITICAL, LIMITED, OFFLINE, UNKNOWN
    private String diagnosis;
    private Double latencyMs, avgLatencyMs, jitterMs;
    private Integer ttl;
    private double packetLossPercent;
    private int healthScore;
    private int consecutiveFailures;
    private boolean local, gateway, sameSegment;
    private Instant firstSeen, lastSeen, lastStateChange, hostnameCheckedAt;
    private List<Issue> issues = new ArrayList<>();
    private final List<Double> latencyHistory = new ArrayList<>();      // newest last, null = probe lost
    private final List<Instant> stateChanges = new ArrayList<>();

    public Device(String ip) {
        this.ip = ip;
    }

    /** Adds one probe result (null = lost) and keeps only the newest maxSize entries. */
    public void pushSample(Double latency, int maxSize) {
        latencyHistory.add(latency);
        while (latencyHistory.size() > maxSize) latencyHistory.remove(0);
    }

    public void recordStateChange(Instant at) {
        stateChanges.add(at);
        while (stateChanges.size() > 12) stateChanges.remove(0);
        lastStateChange = at;
    }

    public int stateChangesWithin(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        int n = 0;
        for (Instant i : stateChanges) if (i.isAfter(cutoff)) n++;
        return n;
    }

    public String getIp() { return ip; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { hostname = v; }
    public String getHostnameSource() { return hostnameSource; }
    public void setHostnameSource(String v) { hostnameSource = v; }
    public String getVendor() { return vendor; }
    public void setVendor(String v) { vendor = v; }
    public String getVendorNote() { return vendorNote; }
    public void setVendorNote(String v) { vendorNote = v; }
    public String getClassificationReason() { return classificationReason; }
    public void setClassificationReason(String v) { classificationReason = v; }
    public String getOsHint() { return osHint; }
    public void setOsHint(String v) { osHint = v; }
    public String getSubnet() { return subnet; }
    public void setSubnet(String v) { subnet = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String v) { deviceType = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public String getLatencyBand() { return latencyBand; }
    public void setLatencyBand(String v) { latencyBand = v; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String v) { healthStatus = v; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String v) { diagnosis = v; }
    public Double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Double v) { latencyMs = v; }
    public Double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Double v) { avgLatencyMs = v; }
    public Double getJitterMs() { return jitterMs; }
    public void setJitterMs(Double v) { jitterMs = v; }
    public Integer getTtl() { return ttl; }
    public void setTtl(Integer v) { ttl = v; }
    public double getPacketLossPercent() { return packetLossPercent; }
    public void setPacketLossPercent(double v) { packetLossPercent = v; }
    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int v) { healthScore = v; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int v) { consecutiveFailures = v; }
    public boolean isLocal() { return local; }
    public void setLocal(boolean v) { local = v; }
    public boolean isGateway() { return gateway; }
    public void setGateway(boolean v) { gateway = v; }
    public boolean isSameSegment() { return sameSegment; }
    public void setSameSegment(boolean v) { sameSegment = v; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant v) { firstSeen = v; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant v) { lastSeen = v; }
    public Instant getLastStateChange() { return lastStateChange; }
    public Instant getHostnameCheckedAt() { return hostnameCheckedAt; }
    public void setHostnameCheckedAt(Instant v) { hostnameCheckedAt = v; }
    public List<Issue> getIssues() { return issues; }
    public void setIssues(List<Issue> v) { issues = v; }
    public List<Double> getLatencyHistory() { return latencyHistory; }
    public void restoreLastStateChange(Instant v) { lastStateChange = v; }
}
