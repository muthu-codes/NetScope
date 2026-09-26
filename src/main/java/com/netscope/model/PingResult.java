package com.netscope.model;

/** Result of a single ICMP echo. latencyMs and ttl are null when the host did not answer. */
public record PingResult(String ip, boolean reachable, Double latencyMs, Integer ttl) {
    public static PingResult failed(String ip) {
        return new PingResult(ip, false, null, null);
    }
}
