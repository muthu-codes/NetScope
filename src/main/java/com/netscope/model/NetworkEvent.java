package com.netscope.model;

import java.time.Instant;

/**
 * type: DEVICE_NEW, DEVICE_ONLINE, DEVICE_OFFLINE, DEVICE_RETURNED, GATEWAY_CHANGED, IP_CHANGED, MAC_CHANGED,
 *       LATENCY_CHANGED, NETWORK_INTERFACE_DOWN, SCAN_COMPLETED
 * severity: INFO, WARNING, CRITICAL
 */
public record NetworkEvent(long id, Instant timestamp, String type, String severity, String ip, String mac,
                           String hostname, String message) {
}
