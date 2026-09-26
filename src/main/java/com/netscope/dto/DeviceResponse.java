package com.netscope.dto;

import com.netscope.model.Device;
import com.netscope.model.Issue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** API shape of a device. Immutable snapshot - safe to hand to other threads. */
public record DeviceResponse(
        String ip, String mac, String hostname, String hostnameSource, String vendor, String vendorNote,
        String deviceType, String classificationReason, String osHint, String state,
        Double latencyMs, Double avgLatencyMs, Double jitterMs, double packetLossPercent, Integer ttl,
        String subnet, boolean sameSegment, boolean local, boolean gateway, String source,
        Instant firstSeen, Instant lastSeen, Instant lastStateChange,
        int healthScore, String healthStatus, List<Issue> issues, String diagnosis, List<Double> latencyHistory) {

    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getIp(), d.getMac(), d.getHostname(), d.getHostnameSource(), d.getVendor(), d.getVendorNote(),
                d.getDeviceType(), d.getClassificationReason(), d.getOsHint(), d.getState(),
                d.getLatencyMs(), d.getAvgLatencyMs(), d.getJitterMs(), d.getPacketLossPercent(), d.getTtl(),
                d.getSubnet(), d.isSameSegment(), d.isLocal(), d.isGateway(), d.getSource(),
                d.getFirstSeen(), d.getLastSeen(), d.getLastStateChange(),
                d.getHealthScore(), d.getHealthStatus(), List.copyOf(d.getIssues()), d.getDiagnosis(),
                new ArrayList<>(d.getLatencyHistory()));
    }

    /** Best human-friendly name: hostname if known, otherwise the IP. */
    public String displayName() {
        return hostname != null && !hostname.isBlank() ? hostname : ip;
    }
}
