package com.netscope.zone;

/** JSON body for creating / updating a zone. Optional fields fall back to safe defaults in ZoneValidator. */
public record ZoneRequest(String name, String cidr, String description, String zoneType, Boolean enabled,
                          Boolean authorized, String authorizedBy, String gateway, String methods, String tcpPorts,
                          Boolean useNmap, Integer maxConcurrency, Integer timeoutMs, Integer maxDevices,
                          Integer intervalSeconds, Integer failureThreshold) {
}
