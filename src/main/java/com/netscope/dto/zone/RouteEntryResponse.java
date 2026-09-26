package com.netscope.dto.zone;

public record RouteEntryResponse(String destination, String nextHop, String iface, String type, Integer metric) {
}
