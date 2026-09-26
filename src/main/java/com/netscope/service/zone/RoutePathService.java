package com.netscope.service.zone;

import com.netscope.config.NetScopeConfig;
import com.netscope.util.CommandExecutor;
import com.netscope.util.NetworkUtils;
import com.netscope.zone.RouteParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer-3 path discovery for zones (separate from the existing single-subnet TracerouteService, which is gated by
 * ScopeService's own AUTO/CONFIGURED scope). A zone's own enabled+authorized flags are the authorization here.
 * A traceroute hop list is an OBSERVED path, never proof of physical topology - see docs/topology.md.
 */
@Service
public class RoutePathService {

    private static final Logger log = LoggerFactory.getLogger(RoutePathService.class);

    private final CommandExecutor exec;
    private final NetScopeConfig config;
    private final Map<Long, List<String>> pathByZone = new ConcurrentHashMap<>();
    private final Map<Long, Instant> tracedAt = new ConcurrentHashMap<>();

    public RoutePathService(CommandExecutor exec, NetScopeConfig config) {
        this.exec = exec;
        this.config = config;
    }

    /** Traces to {@code target} (typically the zone's gateway or a device that just answered a probe). */
    public List<String> trace(Long zoneId, String target) {
        int max = Math.max(3, Math.min(config.topology().maxHops(), 30));
        List<String> cmd = NetworkUtils.isWindows()
                ? List.of("tracert", "-d", "-h", String.valueOf(max), "-w", "500", target)
                : List.of("traceroute", "-n", "-m", String.valueOf(max), "-w", "1", "-q", "1", target);
        String out = exec.run(45_000, cmd).output();
        List<String> hops = new ArrayList<>();
        for (var h : com.netscope.service.TracerouteService.parse("zone", target, out).hops())
            if (h.ip() != null) hops.add(h.ip());
        if (!hops.isEmpty()) {
            pathByZone.put(zoneId, hops);
            tracedAt.put(zoneId, Instant.now());
        }
        return hops;
    }

    public List<String> lastPath(Long zoneId) {
        return pathByZone.getOrDefault(zoneId, List.of());
    }

    public boolean isStale(Long zoneId, int refreshSeconds) {
        Instant t = tracedAt.get(zoneId);
        return t == null || t.isBefore(Instant.now().minusSeconds(refreshSeconds));
    }

    /** Reads the OS routing table (for the "Route table" tab - informational only, not an authorization list). */
    public List<RouteParser.RouteEntry> routeTable() {
        try {
            if (NetworkUtils.isWindows()) {
                String out = exec.run(6000, "route", "print", "-4").output();
                return RouteParser.parseWindows(out);
            }
            String out = exec.run(6000, "ip", "-4", "route").output();
            return RouteParser.parseLinux(out);
        } catch (Exception e) {
            log.debug("Route table read failed: {}", e.getMessage());
            return List.of();
        }
    }
}
