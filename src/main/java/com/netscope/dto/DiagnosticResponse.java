package com.netscope.dto;

import com.netscope.model.DiagnosticResult;
import com.netscope.model.PingStats;

import java.time.Instant;
import java.util.List;

/** overallStatus: HEALTHY, DEGRADED or CRITICAL. */
public record DiagnosticResponse(String target, Instant generatedAt, String overallStatus, String summary,
                                 List<DiagnosticResult> checks, List<DiagnosticFinding> findings,
                                 PingStats pingStats, List<String> path) {
}
