package com.netscope.dto;

public record DeviceStats(int total, int reachable, int unreachable, int arpOnly, int healthy, int degraded,
                          int critical, Double avgLatencyMs, int subnets) {
}
