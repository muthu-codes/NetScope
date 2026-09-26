package com.netscope.model;

/** One diagnostic check. status: PASS, WARN, FAIL or INFO. */
public record DiagnosticResult(String id, String name, String status, String summary, String detail,
                               String recommendation, Long durationMs, Double latencyMs) {
}
