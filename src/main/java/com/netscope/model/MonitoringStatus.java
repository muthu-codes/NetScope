package com.netscope.model;

import java.time.Instant;

public record MonitoringStatus(boolean enabled, boolean scanning, String phase, int progressDone, int progressTotal,
                               String mode, Instant scanStartedAt, Instant lastFullScanAt, Instant lastRefreshAt,
                               Long lastScanDurationMs, Instant nextFullScanAt, int fullScanIntervalSeconds,
                               int refreshIntervalSeconds, String lastError, ScanResult lastResult,
                               boolean baselineComplete) {
}
