package com.netscope.zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns per-zone results into cross-subnet incidents. Wording stays evidence-based ("possible", "observed"). */
public final class IncidentCorrelator {

    private IncidentCorrelator() {
    }

    public static List<Incident> correlate(List<ZoneView> zones) {
        List<Incident> out = new ArrayList<>();
        List<ZoneView> unhealthy = new ArrayList<>();

        for (ZoneView z : zones) {
            if (z.status() == ZoneStatus.UNREACHABLE) {
                unhealthy.add(z);
                List<String> ev = new ArrayList<>();
                ev.add("Zone " + z.name() + " (" + z.cidr() + "): no device answered the configured probes.");
                if (Boolean.TRUE.equals(z.gatewayUp())) {
                    ev.add("Gateway " + z.gateway() + " is reachable, so the path to the gateway works.");
                    out.add(new Incident("ZONE_UNREACHABLE", "CRITICAL", "Zone " + z.name() + " unreachable (gateway reachable)",
                            List.of(z.name()), null, withNote(ev, "Possible zone-level issue: ACL/firewall, VLAN or switch problem, or the zone is idle. A timeout alone cannot tell which.")));
                } else if (Boolean.FALSE.equals(z.gatewayUp())) {
                    ev.add("Gateway " + z.gateway() + " did not answer either.");
                    out.add(new Incident("ZONE_UNREACHABLE", "CRITICAL", "Zone " + z.name() + " unreachable (gateway also silent)",
                            List.of(z.name()), null, withNote(ev, "Possible path or gateway problem. Gateways may also simply block ICMP.")));
                } else {
                    out.add(new Incident("ZONE_UNREACHABLE", "WARNING", "Zone " + z.name() + " unreachable",
                            List.of(z.name()), null, withNote(ev, "No gateway is configured for this zone, so the path could not be separated from the zone itself.")));
                }
            } else if (z.status() == ZoneStatus.DEGRADED) {
                unhealthy.add(z);
                out.add(new Incident("ZONE_DEGRADED", "WARNING", "Zone " + z.name() + " degraded", List.of(z.name()), null,
                        List.of(z.down() + " device(s) DOWN and " + z.degraded() + " degraded out of " + (z.up() + z.down() + z.degraded()) + " known in " + z.cidr() + ".")));
            }
        }

        // Several unhealthy zones that share the same first upstream hop -> possible shared dependency
        Map<String, List<ZoneView>> byHop = new LinkedHashMap<>();
        for (ZoneView z : unhealthy) {
            String hop = upstream(z);
            if (hop != null) byHop.computeIfAbsent(hop, k -> new ArrayList<>()).add(z);
        }
        for (Map.Entry<String, List<ZoneView>> e : byHop.entrySet()) {
            if (e.getValue().size() < 2) continue;
            List<String> names = e.getValue().stream().map(ZoneView::name).toList();
            out.add(new Incident("SHARED_DEPENDENCY", "CRITICAL", "Multiple zones unhealthy through the same observed hop",
                    names, e.getKey(), List.of(
                    "Affected zones: " + String.join(", ", names) + ".",
                    "Observed path: every affected zone's traceroute passes through " + e.getKey() + ".",
                    "Possible common dependency: " + e.getKey() + ". This is correlation, not proof that it caused the problem.")));
        }
        return out;
    }

    /** The router beyond the first hop (the local gateway is shared by everything, so it is a weak signal). */
    static String upstream(ZoneView z) {
        List<String> p = z.path();
        if (p == null || p.isEmpty()) return null;
        return p.size() >= 2 ? p.get(1) : p.get(0);
    }

    private static List<String> withNote(List<String> ev, String note) {
        List<String> l = new ArrayList<>(ev);
        l.add(note);
        return l;
    }
}
