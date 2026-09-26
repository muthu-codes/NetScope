package com.netscope.service.zone;

import com.netscope.entity.NetworkZoneEntity;
import com.netscope.entity.ZoneDeviceEntity;
import com.netscope.repository.ZoneDeviceRepository;
import com.netscope.service.HostnameResolutionService;
import com.netscope.service.PingService;
import com.netscope.util.Cidr;
import com.netscope.zone.BoundedRunner;
import com.netscope.zone.HostState;
import com.netscope.zone.HostStateMachine;
import com.netscope.zone.ZoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Layer-3 reachability + basic service checks for a ROUTED/SERVER/MANAGEMENT/CUSTOM zone.
 * ARP is NOT used here - it does not cross a router. Only ICMP and TCP connect checks are performed,
 * with bounded concurrency and a failure-threshold debounce so one dropped probe never flips a device to DOWN.
 */
@Service
public class ZoneReachabilityService {

    private static final Logger log = LoggerFactory.getLogger(ZoneReachabilityService.class);

    private final PingService ping;
    private final HostnameResolutionService hostnames;
    private final ZoneDeviceRepository repo;

    public ZoneReachabilityService(PingService ping, HostnameResolutionService hostnames, ZoneDeviceRepository repo) {
        this.ping = ping;
        this.hostnames = hostnames;
        this.repo = repo;
    }

    public record Summary(int up, int degraded, int down, int unknown, double avgLatencyMs, Instant finishedAt) {
    }

    public record ScanProgress(int total, AtomicInteger done) {
    }

    /** Runs the probes for every address in the zone's CIDR (capped at maxDevices) and persists results. */
    @Transactional
    public Summary scan(NetworkZoneEntity zone, ScanProgress progress) {
        boolean icmp = zone.getMethods().contains("ICMP");
        boolean tcp = zone.getMethods().contains("TCP");
        List<Integer> ports = ZoneValidator.portList(zone.getTcpPorts());

        List<String> hosts = Cidr.parse(zone.getCidr()).hosts();
        if (hosts.size() > zone.getMaxDevices()) hosts = hosts.subList(0, zone.getMaxDevices());
        if (progress != null) progress.done().set(0);

        Map<String, ZoneDeviceEntity> existing = new ConcurrentHashMap<>();
        for (ZoneDeviceEntity d : repo.findByZoneId(zone.getId())) existing.put(d.getIp(), d);

        AtomicInteger up = new AtomicInteger(), degraded = new AtomicInteger(), down = new AtomicInteger();
        List<Double> latencies = new ArrayList<>();
        Object latLock = new Object();
        List<ZoneDeviceEntity> toSave = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        BoundedRunner.run(hosts, zone.getMaxConcurrency(), (String ip) -> {
            boolean reachable = false;
            Double latency = null;
            String method = null;
            if (icmp) {
                var r = ping.ping(ip, zone.getTimeoutMs());
                if (r.reachable()) {
                    reachable = true;
                    latency = r.latencyMs();
                    method = "ICMP";
                }
            }
            List<TcpProbe.PortResult> portResults = List.of();
            if (tcp && !ports.isEmpty()) {
                portResults = TcpProbe.check(ip, ports, Math.min(zone.getTimeoutMs(), 1500));
                boolean anyOpen = portResults.stream().anyMatch(TcpProbe.PortResult::open);
                if (anyOpen) {
                    reachable = true;
                    if (method == null) {
                        method = "TCP";
                        latency = portResults.stream().filter(TcpProbe.PortResult::open).map(TcpProbe.PortResult::connectMs)
                                .filter(v -> v != null).findFirst().orElse(null);
                    }
                }
            }
            if (progress != null) progress.done().incrementAndGet();
            return new Object[] { ip, reachable, latency, method, portResults };
        }, (Object[] r) -> {
            String ip = (String) r[0];
            boolean reachable = (boolean) r[1];
            Double latency = (Double) r[2];
            String method = (String) r[3];
            @SuppressWarnings("unchecked")
            List<TcpProbe.PortResult> portResults = (List<TcpProbe.PortResult>) r[4];

            ZoneDeviceEntity d = existing.getOrDefault(ip, fresh(zone.getId(), ip));
            HostState previous = safeState(d.getState());
            HostStateMachine.Result next = HostStateMachine.next(previous, d.getConsecutiveFailures(), reachable, zone.getFailureThreshold());
            d.setConsecutiveFailures(next.failures());
            d.setState(next.state().name());
            d.setLatencyMs(latency);
            d.setDiscoveryMethod(method);
            if (reachable) {
                d.setLastSeen(Instant.now());
                if (d.getHostname() == null) {
                    var resolved = hostnames.resolve(ip, false);
                    if (resolved != null) d.setHostname(resolved.hostname());
                }
            }
            String open = portResults.stream().filter(TcpProbe.PortResult::open).map(p -> String.valueOf(p.port()))
                    .reduce((a, b) -> a + "," + b).orElse(null);
            if (open != null) d.setOpenPorts(open);

            switch (next.state()) {
                case UP -> up.incrementAndGet();
                case DEGRADED -> degraded.incrementAndGet();
                case DOWN -> down.incrementAndGet();
                default -> { }
            }
            if (latency != null) synchronized (latLock) { latencies.add(latency); }
            synchronized (toSave) { toSave.add(d); }
        }, cancelled::get);

        repo.saveAll(toSave);
        double avg = latencies.isEmpty() ? 0 : latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        int unknown = hosts.size() - up.get() - degraded.get() - down.get();
        log.info("Zone '{}' scan finished: {} up, {} degraded, {} down, {} unknown", zone.getName(), up.get(), degraded.get(), down.get(), unknown);
        return new Summary(up.get(), degraded.get(), down.get(), Math.max(0, unknown), Math.round(avg * 10) / 10.0, Instant.now());
    }

    private static HostState safeState(String s) {
        try {
            return s == null ? HostState.UNKNOWN : HostState.valueOf(s);
        } catch (IllegalArgumentException e) {
            return HostState.UNKNOWN;
        }
    }

    private static ZoneDeviceEntity fresh(Long zoneId, String ip) {
        ZoneDeviceEntity d = new ZoneDeviceEntity();
        d.setZoneId(zoneId);
        d.setIp(ip);
        d.setState(HostState.UNKNOWN.name());
        return d;
    }
}
