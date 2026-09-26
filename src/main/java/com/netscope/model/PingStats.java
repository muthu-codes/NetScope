package com.netscope.model;

import java.util.List;

/** Result of several ICMP echoes to one host. */
public record PingStats(String ip, int sent, int received, double lossPercent,
                        Double minMs, Double avgMs, Double maxMs, Double jitterMs, List<Double> samples) {
}
