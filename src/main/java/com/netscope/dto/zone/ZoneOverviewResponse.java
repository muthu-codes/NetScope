package com.netscope.dto.zone;

import java.util.List;

public record ZoneOverviewResponse(int zones, int devices, int online, int degraded, int offline, int services,
                                   int activeIncidents, List<ZoneHealth> zoneHealth, boolean nmapAvailable,
                                   String nmapVersion) {
    public record ZoneHealth(long id, String name, String status, int up, int degraded, int down) {
    }
}
