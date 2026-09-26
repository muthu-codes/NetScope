package com.netscope.model;

import java.time.Instant;
import java.util.List;

public record ScanResult(long sessionId, String mode, Instant startedAt, Instant finishedAt, long durationMs,
                         List<String> scopes, int targetsProbed, int devicesReachable, int devicesArpOnly,
                         int devicesTotal, int newDevices, int wentOffline) {
}
