package com.netscope.service.zone;

import com.netscope.dto.zone.*;
import com.netscope.entity.NetworkZoneEntity;
import com.netscope.entity.ZoneDeviceEntity;
import com.netscope.model.NetworkConfiguration;
import com.netscope.repository.NetworkZoneRepository;
import com.netscope.repository.ZoneDeviceRepository;
import com.netscope.service.EventService;
import com.netscope.service.NetworkInfoService;
import com.netscope.util.Cidr;
import com.netscope.util.NetworkUtils;
import com.netscope.websocket.NetworkEventWebSocketHandler;
import com.netscope.zone.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.netscope.entity.DeviceEntity;
import com.netscope.repository.DeviceRepository;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRUD + scan orchestration for Network Zones. This is the multi-subnet layer added on top of NetScope's existing
 * single-subnet discovery: the LOCAL zone below is a thin view over the existing DeviceDiscoveryService/ARP data,
 * every other zone type is monitored with Layer-3 checks (ZoneReachabilityService), never ARP.
 */
@Service
public class ZoneService {

    private static final Logger log = LoggerFactory.getLogger(ZoneService.class);

    private final NetworkZoneRepository zones;
    private final ZoneDeviceRepository zoneDevices;
    private final DeviceRepository devices;
    private final ZoneReachabilityService reachability;
    private final RoutePathService routePath;
    private final NmapService nmap;
    private final NetworkInfoService networkInfo;
    private final EventService events;
    private final NetworkEventWebSocketHandler ws;

    private final ExecutorService scanPool = Executors.newFixedThreadPool(4, NetworkUtils.daemonFactory("zone-scan"));
    private final Map<Long, ZoneReachabilityService.ScanProgress> progress = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> running = new ConcurrentHashMap<>();

   public ZoneService(NetworkZoneRepository zones, ZoneDeviceRepository zoneDevices,
                   DeviceRepository devices,
                   ZoneReachabilityService reachability,
                   RoutePathService routePath, NmapService nmap,
                   NetworkInfoService networkInfo, EventService events,
                   NetworkEventWebSocketHandler ws) {
    this.zones = zones;
    this.zoneDevices = zoneDevices;
    this.devices = devices;
    this.reachability = reachability;
    this.routePath = routePath;
    this.nmap = nmap;
    this.networkInfo = networkInfo;
    this.events = events;
    this.ws = ws;
}

    /** Adds the directly-connected subnet as a LOCAL zone automatically. Never adds anything else on its own. */
    @PostConstruct
    void autoDetectLocalZone() {
        try {
            NetworkConfiguration cfg = networkInfo.configuration();
            if (cfg == null || cfg.networkCidr() == null) return;
            if (zones.existsByCidr(cfg.networkCidr())) return;
            NetworkZoneEntity z = new NetworkZoneEntity();
            z.setName(cfg.interfaceDisplayName() != null ? cfg.interfaceDisplayName() : "Local network");
            z.setCidr(cfg.networkCidr());
            z.setDescription("Automatically detected from this computer's active network interface.");
            z.setZoneType(ZoneType.LOCAL.name());
            z.setEnabled(true);
            z.setAuthorized(true);
            z.setAuthorizedBy("local subnet (auto-detected)");
            z.setGateway(cfg.gatewayIp());
            z.setMethods("ICMP,TCP");
            z.setStatus(ZoneStatus.MONITORING.name());
            zones.save(z);
            log.info("Auto-added LOCAL zone {} ({})", z.getName(), z.getCidr());
        } catch (Exception e) {
            log.debug("Local zone auto-detection skipped: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- CRUD

    public List<ZoneResponse> list() {
        return zones.findAll().stream().map(this::toResponse).toList();
    }

    public ZoneResponse get(long id) {
        return toResponse(find(id));
    }

    public ZoneResponse create(ZoneRequest req) {
        ZoneValidator.Normalized n = ZoneValidator.validate(req);
        if (zones.existsByCidr(n.cidr()))
            throw new IllegalArgumentException("A zone for " + n.cidr() + " already exists.");
        NetworkZoneEntity z = new NetworkZoneEntity();
        apply(z, n);
        z.setStatus(ZoneStatus.IDLE.name());
        z = zones.save(z);
        events.emit("ZONE_CREATED", "INFO", null, null, null, "Zone '" + z.getName() + "' (" + z.getCidr() + ") created.");
        return toResponse(z);
    }

    public ZoneResponse update(long id, ZoneRequest req) {
        NetworkZoneEntity z = find(id);
        ZoneValidator.Normalized n = ZoneValidator.validate(req);
        zones.findByCidr(n.cidr()).ifPresent(other -> {
            if (!other.getId().equals(id)) throw new IllegalArgumentException("Another zone already uses " + n.cidr() + ".");
        });
        apply(z, n);
        zones.save(z);
        return toResponse(z);
    }

    public void delete(long id) {
        NetworkZoneEntity z = find(id);
        zoneDevices.deleteByZoneId(id);
        zones.delete(z);
        events.emit("ZONE_DELETED", "INFO", null, null, null, "Zone '" + z.getName() + "' deleted.");
    }

    public ZoneResponse setEnabled(long id, boolean enabled) {
        NetworkZoneEntity z = find(id);
        z.setEnabled(enabled);
        if (!enabled) z.setStatus(ZoneStatus.IDLE.name());
        zones.save(z);
        return toResponse(z);
    }

    private void apply(NetworkZoneEntity z, ZoneValidator.Normalized n) {
        z.setName(n.name());
        z.setCidr(n.cidr());
        z.setDescription(n.description());
        z.setZoneType(n.type().name());
        z.setEnabled(n.enabled());
        z.setAuthorized(n.authorized());
        z.setAuthorizedBy(n.authorizedBy());
        z.setGateway(n.gateway().isEmpty() ? null : n.gateway());
        z.setMethods(n.methods());
        z.setTcpPorts(n.tcpPorts());
        z.setUseNmap(n.useNmap());
        z.setMaxConcurrency(n.maxConcurrency());
        z.setTimeoutMs(n.timeoutMs());
        z.setMaxDevices(n.maxDevices());
        z.setIntervalSeconds(n.intervalSeconds());
        z.setFailureThreshold(n.failureThreshold());
    }

    private NetworkZoneEntity find(long id) {
        return zones.findById(id).orElseThrow(() -> new IllegalArgumentException("No zone with id " + id + "."));
    }

    // ---------------------------------------------------------------- scanning

    public boolean isRunning(long id) {
        return running.getOrDefault(id, false);
    }

    public ZoneScanStatusResponse scanStatus(long id) {
        NetworkZoneEntity z = find(id);
        var p = progress.get(id);
        int total = p == null ? 0 : p.total();
        int done = p == null ? 0 : p.done().get();
        return new ZoneScanStatusResponse(id, z.getName(), isRunning(id), total, done, isRunning(id) ? "scanning" : "idle");
    }

    /** Starts a scan in the background and returns immediately. */
    public void startScan(long id) {
        NetworkZoneEntity z = find(id);
        if (!z.isEnabled()) throw new IllegalStateException("Zone '" + z.getName() + "' is disabled.");
        if (!z.isAuthorized())
            throw new IllegalStateException("Zone '" + z.getName() + "' is not authorized. Set 'authorized' with an 'authorized by' name before scanning.");
        if (Boolean.TRUE.equals(running.get(id))) return;
        running.put(id, true);
        scanPool.submit(() -> {
            try {
                runScan(z.getId());
            } catch (Exception e) {
                log.warn("Zone scan failed for {}: {}", z.getName(), e.getMessage());
                events.emit("ZONE_SCAN_ERROR", "WARNING", null, null, null, "Scan of '" + z.getName() + "' failed: " + e.getMessage());
            } finally {
                running.put(id, false);
                ws.broadcast("ZONE_SCAN_COMPLETE", Map.of("zoneId", id));
            }
        });
    }

    private void runScan(long id) {
        NetworkZoneEntity z = find(id);
        String previousStatus = z.getStatus();
        z.setStatus(ZoneStatus.DISCOVERING.name());
        zones.save(z);
        int totalHosts = (int) Math.min(z.getMaxDevices(), Cidr.parse(z.getCidr()).hostCount());
        ZoneReachabilityService.ScanProgress p = new ZoneReachabilityService.ScanProgress(totalHosts, new AtomicInteger(0));
        progress.put(id, p);
        Map<String, Object> progressPayload = new java.util.LinkedHashMap<>();
        progressPayload.put("zoneId", id);
        progressPayload.put("total", totalHosts);
        progressPayload.put("done", 0);
        ws.broadcast("ZONE_SCAN_PROGRESS", progressPayload);

        ZoneReachabilityService.Summary summary;
        if (z.getZoneType().equals(ZoneType.LOCAL.name())) {
            summary = scanLocalZoneView(z);
        } else {
            summary = reachability.scan(z, p);
        }

        // route/path refresh - trace to the gateway if configured, else to any reachable device
        try {
            String target = z.getGateway();
            if ((target == null || target.isBlank()) && !z.getZoneType().equals(ZoneType.LOCAL.name())) {
                target = zoneDevices.findByZoneId(id).stream().filter(d -> "UP".equals(d.getState())).map(ZoneDeviceEntity::getIp).findFirst().orElse(null);
            }
            if (target != null && !target.isBlank()) routePath.trace(id, target);
        } catch (Exception e) {
            log.debug("Route trace skipped for zone {}: {}", z.getName(), e.getMessage());
        }

        String newStatus = summary.down() > 0 && summary.up() == 0 && summary.degraded() == 0 ? ZoneStatus.UNREACHABLE.name()
                : summary.down() > 0 || summary.degraded() > 0 ? ZoneStatus.DEGRADED.name() : ZoneStatus.MONITORING.name();
        z.setStatus(newStatus);
        z.setLastScanAt(Instant.now());
        zones.save(z);

        if (!newStatus.equals(previousStatus)) {
            String sev = newStatus.equals(ZoneStatus.UNREACHABLE.name()) ? "CRITICAL" : newStatus.equals(ZoneStatus.DEGRADED.name()) ? "WARNING" : "INFO";
            events.emit("ZONE_" + newStatus, sev, null, null, null,
                    "Zone '" + z.getName() + "' (" + z.getCidr() + ") is now " + newStatus + " (" + summary.up() + " up, "
                            + summary.degraded() + " degraded, " + summary.down() + " down).");
        }
    }

    /** For a LOCAL zone we reuse the existing NetScope discovery data instead of re-scanning with our own engine. */
    private ZoneReachabilityService.Summary scanLocalZoneView(NetworkZoneEntity z)
     {
    Cidr cidr = Cidr.parse(z.getCidr());

    int total = 0;
    int up = 0;
    int degraded = 0;
    int down = 0;
    double latencySum = 0;
    int latencyCount = 0;

    for (DeviceEntity d : devices.findAll()) {
        if (!cidr.contains(d.getIp())) {
            continue;
        }

        ZoneDeviceEntity zd = zoneDevices
                .findByZoneIdAndIp(z.getId(), d.getIp())
                .orElseGet(() -> {
                    ZoneDeviceEntity x = new ZoneDeviceEntity();
                    x.setZoneId(z.getId());
                    x.setIp(d.getIp());
                    return x;
                });

        zd.setMac(d.getMac());
        zd.setHostname(d.getHostname());
        zd.setState(d.getState() != null ? d.getState() : "UNKNOWN");
        zd.setDiscoveryMethod("LOCAL");
        zd.setLatencyMs(d.getLastLatencyMs());
        zd.setLastSeen(d.getLastSeen());

        zoneDevices.save(zd);

        total++;

        String state = zd.getState();
        if ("UP".equalsIgnoreCase(state)) {
            up++;
        } else if ("DEGRADED".equalsIgnoreCase(state)) {
            degraded++;
        } else if ("DOWN".equalsIgnoreCase(state)) {
            down++;
        }

        if (d.getLastLatencyMs() != null) {
            latencySum += d.getLastLatencyMs();
            latencyCount++;
        }
    }

    double avgLatency = latencyCount == 0 ? 0 : latencySum / latencyCount;

    return new ZoneReachabilityService.Summary(
            total,
            up,
            degraded,
            down,
            avgLatency,
            Instant.now()
    );
    }

    public NmapScanResponse nmapScan(long id) {
        NetworkZoneEntity z = find(id);
        NmapService.ScanOutcome o = nmap.scanZone(z);
        List<NmapScanResponse.Host> hosts = o.hosts().stream()
                .map(h -> new NmapScanResponse.Host(h.ip(), h.hostname(), h.latencyMs(),
                        h.openPorts().stream().map(p -> new NmapScanResponse.Port(p.port(), p.service())).toList()))
                .toList();
        // merge open ports into stored zone devices so the Devices tab benefits from the deeper scan
        for (var h : o.hosts()) {
            zoneDevices.findByZoneIdAndIp(id, h.ip()).ifPresentOrElse(d -> {
                if (!h.openPorts().isEmpty())
                    d.setOpenPorts(h.openPorts().stream().map(p -> String.valueOf(p.port())).reduce((a, b) -> a + "," + b).orElse(d.getOpenPorts()));
                if (h.hostname() != null) d.setHostname(h.hostname());
                zoneDevices.save(d);
            }, () -> { });
        }
        return new NmapScanResponse(o.ranNmap(), o.reason(), hosts.size(), hosts);
    }

    // ---------------------------------------------------------------- reads

    public List<ZoneDeviceResponse> devices(long id) {
        return zoneDevices.findByZoneId(id).stream().map(this::toResponse).toList();
    }

    public List<RouteEntryResponse> routeTable() {
        return routePath.routeTable().stream()
                .map(r -> new RouteEntryResponse(r.destination(), r.nextHop(), r.iface(), r.type(), r.metric())).toList();
    }

    public ZoneOverviewResponse overview() {
        List<NetworkZoneEntity> all = zones.findAll();
        int devicesTotal = 0, online = 0, degraded = 0, offline = 0, services = 0;
        List<ZoneOverviewResponse.ZoneHealth> health = new ArrayList<>();
        for (NetworkZoneEntity z : all) {
            List<ZoneDeviceEntity> ds = zoneDevices.findByZoneId(z.getId());
            int up = (int) ds.stream().filter(d -> "UP".equals(d.getState())).count();
            int deg = (int) ds.stream().filter(d -> "DEGRADED".equals(d.getState())).count();
            int down = (int) ds.stream().filter(d -> "DOWN".equals(d.getState())).count();
            devicesTotal += ds.size();
            online += up;
            degraded += deg;
            offline += down;
            services += (int) ds.stream().filter(d -> d.getOpenPorts() != null && !d.getOpenPorts().isBlank()).count();
            health.add(new ZoneOverviewResponse.ZoneHealth(z.getId(), z.getName(), z.getStatus(), up, deg, down));
        }
        List<Incident> incidents = IncidentCorrelator.correlate(zoneViews(all));
        return new ZoneOverviewResponse(all.size(), devicesTotal, online, degraded, offline, services, incidents.size(),
                health, nmap.available(), nmap.version());
    }

    public List<IncidentResponse> incidents() {
        return IncidentCorrelator.correlate(zoneViews(zones.findAll())).stream()
                .map(i -> new IncidentResponse(i.type(), i.severity(), i.title(), i.affectedZones(), i.commonDependency(), i.evidence()))
                .toList();
    }

    public ZoneTopologyResponse topology() {
        String hostIp = networkInfo.configuration() == null ? null : networkInfo.configuration().ipv4();
        var g = ZoneTopologyBuilder.build(zoneViews(zones.findAll()), hostIp);
        return new ZoneTopologyResponse(g.nodes(), g.edges(), g.disclaimer());
    }

    private List<ZoneView> zoneViews(List<NetworkZoneEntity> all) {
        List<ZoneView> out = new ArrayList<>();
        for (NetworkZoneEntity z : all) {
            List<ZoneDeviceEntity> ds = zoneDevices.findByZoneId(z.getId());
            int up = (int) ds.stream().filter(d -> "UP".equals(d.getState())).count();
            int deg = (int) ds.stream().filter(d -> "DEGRADED".equals(d.getState())).count();
            int down = (int) ds.stream().filter(d -> "DOWN".equals(d.getState())).count();
            Boolean gwUp = null;
            if (z.getGateway() != null && !z.getGateway().isBlank())
                gwUp = zoneDevices.findByZoneIdAndIp(z.getId(), z.getGateway()).map(d -> "UP".equals(d.getState())).orElse(null);
            out.add(new ZoneView(z.getId(), z.getName(), z.getCidr(), ZoneType.valueOf(z.getZoneType()),
                    ZoneStatus.valueOf(z.getStatus()), z.getGateway(), gwUp, up, deg, down, routePath.lastPath(z.getId())));
        }
        return out;
    }

    private ZoneResponse toResponse(NetworkZoneEntity z) {
        List<ZoneDeviceEntity> ds = zoneDevices.findByZoneId(z.getId());
        int up = (int) ds.stream().filter(d -> "UP".equals(d.getState())).count();
        int deg = (int) ds.stream().filter(d -> "DEGRADED".equals(d.getState())).count();
        int down = (int) ds.stream().filter(d -> "DOWN".equals(d.getState())).count();
        int unknown = (int) ds.stream().filter(d -> "UNKNOWN".equals(d.getState())).count();
        double avg = ds.stream().map(ZoneDeviceEntity::getLatencyMs).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0);
        Boolean gwUp = null;
        if (z.getGateway() != null && !z.getGateway().isBlank())
            gwUp = zoneDevices.findByZoneIdAndIp(z.getId(), z.getGateway()).map(d -> "UP".equals(d.getState())).orElse(null);
        return new ZoneResponse(z.getId(), z.getName(), z.getCidr(), z.getDescription(), z.getZoneType(), z.isEnabled(),
                z.isAuthorized(), z.getAuthorizedBy(), z.getGateway(), gwUp, z.getMethods(), ZoneValidator.portList(z.getTcpPorts()),
                z.isUseNmap(), z.getMaxConcurrency(), z.getTimeoutMs(), z.getMaxDevices(), z.getIntervalSeconds(),
                z.getFailureThreshold(), z.getStatus(), ds.size(), up, deg, down, unknown,
                ds.isEmpty() ? null : Math.round(avg * 10) / 10.0, z.getLastScanAt(), routePath.lastPath(z.getId()));
    }

    private ZoneDeviceResponse toResponse(ZoneDeviceEntity d) {
        List<Integer> ports = d.getOpenPorts() == null || d.getOpenPorts().isBlank() ? List.of()
                : List.of(d.getOpenPorts().split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
        String macNote = d.getMac() != null ? null : "Not available from monitoring host (remote routed device - MAC needs infrastructure-level data such as SNMP).";
        return new ZoneDeviceResponse(d.getIp(), d.getMac(), d.getHostname(), d.getState(), d.getDiscoveryMethod(),
                d.getLatencyMs(), d.getPacketLossPercent(), ports, d.getFirstSeen(), d.getLastSeen(), macNote);
    }
}
