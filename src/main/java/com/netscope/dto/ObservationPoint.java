package com.netscope.dto;

import java.time.Instant;

public record ObservationPoint(Instant at, boolean reachable, Double latencyMs) {
}
