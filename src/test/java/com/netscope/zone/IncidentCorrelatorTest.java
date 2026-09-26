package com.netscope.zone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentCorrelatorTest {

    @Test
    void detectsSharedUpstreamDependency() {
        ZoneView a = new ZoneView(1, "Lab A", "10.1.0.0/24", ZoneType.ROUTED, ZoneStatus.UNREACHABLE, "10.1.0.1", true, 0, 0, 5, List.of("10.0.0.11"));
        ZoneView b = new ZoneView(2, "Lab B", "10.2.0.0/24", ZoneType.ROUTED, ZoneStatus.UNREACHABLE, "10.2.0.1", true, 0, 0, 5, List.of("10.0.0.11"));
        var incidents = IncidentCorrelator.correlate(List.of(a, b));
        assertTrue(incidents.stream().anyMatch(i -> i.type().equals("SHARED_DEPENDENCY") && "10.0.0.11".equals(i.commonDependency())));
    }

    @Test
    void singleUnreachableZoneIsNotACorrelation() {
        ZoneView a = new ZoneView(1, "Lab A", "10.1.0.0/24", ZoneType.ROUTED, ZoneStatus.UNREACHABLE, "10.1.0.1", true, 0, 0, 5, List.of("10.0.0.11"));
        var incidents = IncidentCorrelator.correlate(List.of(a));
        assertTrue(incidents.stream().noneMatch(i -> i.type().equals("SHARED_DEPENDENCY")));
        assertTrue(incidents.stream().anyMatch(i -> i.type().equals("ZONE_UNREACHABLE")));
    }
}
