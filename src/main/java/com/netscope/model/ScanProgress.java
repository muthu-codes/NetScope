package com.netscope.model;

import java.time.Instant;

public record ScanProgress(boolean running, String mode, String phase, int done, int total, Instant startedAt) {
    public static ScanProgress idle() {
        return new ScanProgress(false, null, "Idle", 0, 0, null);
    }
}
