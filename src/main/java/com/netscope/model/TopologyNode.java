package com.netscope.model;

/**
 * A node in the topology graph.
 * type: LOCAL_HOST, GATEWAY, ROUTER, SUBNET, INTERNET, or a device type (COMPUTER, MOBILE, PRINTER, ...).
 * source says HOW we know this node exists (e.g. "scan", "traceroute", "routing-table").
 */
public record TopologyNode(String id, String label, String type, String ip, String mac, String vendor,
                           String state, Double latencyMs, String healthStatus, Integer healthScore,
                           String source, String subnet, Integer deviceCount, Integer onlineCount, boolean virtual) {
}
