package com.netscope.dto;

public record SubnetSummary(String subnet, int total, int online, int offline, int problems, Double avgLatencyMs,
                            String worstHealth) {
}
