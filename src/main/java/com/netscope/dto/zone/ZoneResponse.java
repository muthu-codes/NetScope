package com.netscope.dto.zone;

import java.time.Instant;
import java.util.List;

public record ZoneResponse(long id, String name, String cidr, String description, String zoneType, boolean enabled,
                           boolean authorized, String authorizedBy, String gateway, Boolean gatewayUp, String methods,
                           List<Integer> tcpPorts, boolean useNmap, int maxConcurrency, int timeoutMs, int maxDevices,
                           int intervalSeconds, int failureThreshold, String status, int deviceCount, int up,
                           int degraded, int down, int unknown, Double avgLatencyMs, Instant lastScanAt,
                           List<String> observedPath) {
}
