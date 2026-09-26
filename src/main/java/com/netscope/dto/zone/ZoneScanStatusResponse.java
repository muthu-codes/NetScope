package com.netscope.dto.zone;

public record ZoneScanStatusResponse(long zoneId, String zoneName, boolean running, int total, int done, String rate) {
}
