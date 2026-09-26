package com.netscope.dto.zone;

import java.time.Instant;
import java.util.List;

public record ZoneDeviceResponse(String ip, String mac, String hostname, String state, String discoveryMethod,
                                 Double latencyMs, Double packetLossPercent, List<Integer> openPorts,
                                 Instant firstSeen, Instant lastSeen, String macNote) {
}
