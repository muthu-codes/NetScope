package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.dto.DeviceResponse;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer-3 path discovery (tracert / traceroute). Each hop is a router that REALLY forwarded our packets, which is
 * how NetScope can draw routed college subnets without guessing. We trace to ONE reachable device per remote subnet.
 * Traceroute only sends normal ICMP/UDP probes with increasing TTL to a device that is already inside the scope.
 */
@Service
public class TracerouteService {

    private static final Logger log = LoggerFactory.getLogger(TracerouteService.class);
    private static final Pattern HOP_LINE = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(\\d{1,3}(?:\\.\\d{1,3}){3})(?![\\d.])");
    private static final Pattern RTT = Pattern.compile("([<]?)\\s*(\\d+(?:\\.\\d+)?)\\s*ms");

    public record Hop(int index, String ip, Double rttMs) {
    }

    public record TraceResult(String subnet, String target, List<Hop> hops, boolean reachedTarget, Instant at) {
    }

    private final CommandExecutor exec;
    private final NetScopeConfig config;
    private final ScopeService scopeService;
    private final Map<String, TraceResult> traces = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, NetworkUtils.daemonFactory("traceroute"));

    public TracerouteService(CommandExecutor exec, NetScopeConfig config, ScopeService scopeService) {
        this.exec = exec;
        this.config = config;
        this.scopeService = scopeService;
    }

    /** On-demand trace to a device inside the authorized scope. */
    public TraceResult trace(String ip) {
        scopeService.assertInScope(ip);
        int max = Math.max(3, Math.min(config.topology().maxHops(), 30));
        List<String> cmd = NetworkUtils.isWindows()
                ? List.of("tracert", "-d", "-h", String.valueOf(max), "-w", "500", ip)
                : List.of("traceroute", "-n", "-m", String.valueOf(max), "-w", "1", "-q", "1", ip);
        String out = exec.run(45_000, cmd).output();
        return parse(IpAddressUtils.subnet24(ip), ip, out);
    }

    /** Re-traces (at most every trace-refresh-seconds) to one representative device per routed subnet. */
    public void refresh(List<DeviceResponse> devices) {
        if (!config.topology().tracerouteEnabled()) return;
        Map<String, DeviceResponse> reps = new LinkedHashMap<>();
        for (DeviceResponse d : devices) {
            if (!"REACHABLE".equals(d.state()) || d.local() || d.gateway() || d.sameSegment() || d.subnet() == null) continue;
            DeviceResponse cur = reps.get(d.subnet());
            double lat = d.latencyMs() == null ? Double.MAX_VALUE : d.latencyMs();
            double curLat = cur == null || cur.latencyMs() == null ? Double.MAX_VALUE : cur.latencyMs();
            if (cur == null || lat < curLat) reps.put(d.subnet(), d);
        }
        traces.keySet().removeIf(s -> !reps.containsKey(s));

        Instant staleBefore = Instant.now().minus(Duration.ofSeconds(config.topology().traceRefreshSeconds()));
        List<Future<?>> futures = new ArrayList<>();
        int started = 0;
        for (Map.Entry<String, DeviceResponse> e : reps.entrySet()) {
            if (started >= config.topology().maxTracedSubnets()) break;
            TraceResult old = traces.get(e.getKey());
            if (old != null && old.at().isAfter(staleBefore)) continue;
            started++;
            String ip = e.getValue().ip();
            futures.add(pool.submit(() -> {
                try {
                    TraceResult r = trace(ip);
                    traces.put(e.getKey(), r);
                } catch (Exception ex) {
                    log.debug("Trace to {} failed: {}", ip, ex.getMessage());
                }
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.MINUTES);
            } catch (Exception ignored) {
                f.cancel(true);
            }
        }
        if (started > 0) log.info("Traceroute refreshed for {} routed subnet(s)", started);
    }

    public Collection<TraceResult> results() {
        return List.copyOf(traces.values());
    }

    public Set<String> hopIps() {
        Set<String> s = new HashSet<>();
        for (TraceResult r : traces.values()) for (Hop h : r.hops()) if (h.ip() != null && !h.ip().equals(r.target())) s.add(h.ip());   // the target itself is not a router
        return s;
    }

    /** Parses Windows tracert or Linux/macOS traceroute output. Hops that did not answer have ip == null. */
    public static TraceResult parse(String subnet, String target, String output) {
        List<Hop> hops = new ArrayList<>();
        if (output != null) {
            for (String line : output.split("\\R")) {
                Matcher m = HOP_LINE.matcher(line);
                if (!m.matches()) continue;
                int idx = Integer.parseInt(m.group(1));
                String rest = m.group(2);
                Matcher ip = IPV4.matcher(rest);
                String hopIp = ip.find() && IpAddressUtils.isValidIpv4(ip.group(1)) ? ip.group(1) : null;
                Double rtt = null;
                Matcher t = RTT.matcher(rest);
                if (t.find()) {
                    double v = Double.parseDouble(t.group(2));
                    rtt = t.group(1).equals("<") ? v / 2.0 : v;
                }
                hops.add(new Hop(idx, hopIp, rtt));
            }
        }
        boolean reached = !hops.isEmpty() && target.equals(hops.get(hops.size() - 1).ip());
        return new TraceResult(subnet, target, hops, reached, Instant.now());
    }
}
