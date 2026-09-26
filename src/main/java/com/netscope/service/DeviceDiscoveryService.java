package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.dto.DeviceResponse;
import com.netscope.dto.DeviceStats;
import com.netscope.dto.SubnetSummary;
import com.netscope.model.*;
import com.netscope.util.Cidr;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.NetworkUtils;
import com.netscope.websocket.NetworkEventWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The heart of NetScope. One scan =
 *   1. ask ScopeService which addresses are allowed,
 *   2. ping them (rate controlled),
 *   3. read the ARP table (MAC addresses + devices that hide from ping),
 *   4. resolve names, look up vendors, classify,
 *   5. merge into the live registry, detect events, score health.
 * FULL scans probe the whole authorized scope. REFRESH scans re-check only devices we already know (fast).
 */
@Service
public class DeviceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDiscoveryService.class);

    private record PendingEvent(String type, String severity, String ip, String mac, String hostname, String message) {
    }

    private record Outcome(List<PendingEvent> events, Set<String> changedIps, int newDevices, int wentOffline) {
    }

    private final NetScopeConfig config;
    private final NetworkInfoService networkInfo;
    private final ScopeService scopeService;
    private final PingService pingService;
    private final NeighborTableService neighbors;
    private final HostnameResolutionService hostnames;
    private final DeviceIntelligenceService intelligence;
    private final HealthAnalysisService health;
    private final EventService events;
    private final PersistenceService persistence;
    private final NetworkEventWebSocketHandler ws;

    private final Map<String, Device> registry = new LinkedHashMap<>();       // guarded by synchronized(registry)
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private volatile ScanProgress progress = ScanProgress.idle();
    private volatile boolean baselineComplete = false;
    private volatile String lastGatewayIp;
    private volatile boolean interfaceWasDown = false;

    public DeviceDiscoveryService(NetScopeConfig config, NetworkInfoService networkInfo, ScopeService scopeService,
                                  PingService pingService, NeighborTableService neighbors,
                                  HostnameResolutionService hostnames, DeviceIntelligenceService intelligence,
                                  HealthAnalysisService health, EventService events, PersistenceService persistence,
                                  NetworkEventWebSocketHandler ws) {
        this.config = config;
        this.networkInfo = networkInfo;
        this.scopeService = scopeService;
        this.pingService = pingService;
        this.neighbors = neighbors;
        this.hostnames = hostnames;
        this.intelligence = intelligence;
        this.health = health;
        this.events = events;
        this.persistence = persistence;
        this.ws = ws;
    }

    @PostConstruct
    void restore() {
        for (Device d : persistence.loadKnownDevices()) registry.put(d.getIp(), d);
        baselineComplete = !registry.isEmpty();
        log.info("Restored {} known devices from history (state UNKNOWN until the first scan)", registry.size());
    }

    // ------------------------------------------------------------------ public read API

    public boolean isScanning() {
        return scanning.get();
    }

    public ScanProgress progress() {
        return progress;
    }

    public boolean baselineComplete() {
        return baselineComplete;
    }

    public List<DeviceResponse> devices() {
        synchronized (registry) {
            List<DeviceResponse> out = new ArrayList<>(registry.size());
            for (Device d : registry.values()) out.add(DeviceResponse.from(d));
            out.sort(Comparator.comparingLong(d -> IpAddressUtils.toLong(d.ip())));
            return out;
        }
    }

    public Optional<DeviceResponse> device(String ip) {
        synchronized (registry) {
            Device d = registry.get(ip);
            return d == null ? Optional.empty() : Optional.of(DeviceResponse.from(d));
        }
    }

    public Optional<DeviceResponse> gatewayDevice() {
        synchronized (registry) {
            for (Device d : registry.values()) if (d.isGateway()) return Optional.of(DeviceResponse.from(d));
            return Optional.empty();
        }
    }

    public DeviceStats stats() {
        List<DeviceResponse> list = devices();
        int reachable = 0, unreachable = 0, arpOnly = 0, healthy = 0, degraded = 0, critical = 0, latN = 0;
        double latSum = 0;
        Set<String> subnets = new HashSet<>();
        for (DeviceResponse d : list) {
            if (d.subnet() != null) subnets.add(d.subnet());
            switch (d.state()) {
                case "REACHABLE" -> reachable++;
                case "UNREACHABLE" -> unreachable++;
                case "ARP_ONLY" -> arpOnly++;
                default -> {
                }
            }
            switch (d.healthStatus()) {
                case "HEALTHY" -> healthy++;
                case "DEGRADED" -> degraded++;
                case "CRITICAL" -> critical++;
                default -> {
                }
            }
            if ("REACHABLE".equals(d.state()) && !d.local() && d.latencyMs() != null) {
                latSum += d.latencyMs();
                latN++;
            }
        }
        Double avg = latN == 0 ? null : Math.round(latSum / latN * 10.0) / 10.0;
        return new DeviceStats(list.size(), reachable, unreachable, arpOnly, healthy, degraded, critical, avg, subnets.size());
    }

    public List<SubnetSummary> subnets() {
        Map<String, List<DeviceResponse>> groups = new TreeMap<>(Comparator.comparingLong(s -> IpAddressUtils.toLong(s.substring(0, s.indexOf('/')))));
        for (DeviceResponse d : devices()) if (d.subnet() != null) groups.computeIfAbsent(d.subnet(), k -> new ArrayList<>()).add(d);
        List<SubnetSummary> out = new ArrayList<>();
        for (Map.Entry<String, List<DeviceResponse>> e : groups.entrySet()) {
            int online = 0, offline = 0, problems = 0, latN = 0;
            double latSum = 0;
            boolean anyCritical = false, anyDegraded = false, anyGood = false;
            for (DeviceResponse d : e.getValue()) {
                switch (d.state()) {
                    case "REACHABLE", "ARP_ONLY" -> online++;
                    case "UNREACHABLE" -> offline++;
                    default -> {
                    }
                }
                if ("CRITICAL".equals(d.healthStatus())) anyCritical = true;
                if ("DEGRADED".equals(d.healthStatus())) anyDegraded = true;
                if ("HEALTHY".equals(d.healthStatus()) || "LIMITED".equals(d.healthStatus())) anyGood = true;
                if ("CRITICAL".equals(d.healthStatus()) || "DEGRADED".equals(d.healthStatus())) problems++;
                if ("REACHABLE".equals(d.state()) && !d.local() && d.latencyMs() != null) {
                    latSum += d.latencyMs();
                    latN++;
                }
            }
            String worst = anyCritical ? "CRITICAL" : anyDegraded ? "DEGRADED" : anyGood ? "HEALTHY" : offline > 0 ? "OFFLINE" : "UNKNOWN";
            out.add(new SubnetSummary(e.getKey(), e.getValue().size(), online, offline, problems,
                    latN == 0 ? null : Math.round(latSum / latN * 10.0) / 10.0, worst));
        }
        return out;
    }

    // ------------------------------------------------------------------ scanning

    public ScanResult runScan(ScanMode mode) {
        if (!scanning.compareAndSet(false, true)) throw new IllegalStateException("A scan is already running.");
        Instant started = Instant.now();
        long sessionId = persistence.startSession(mode.name());
        try {
            setProgress(mode, "Preparing scan", 0, 0, started);
            ScanResult result = doScan(mode, sessionId, started);
            persistence.finishSession(sessionId, result);
            return result;
        } catch (RuntimeException e) {
            persistence.failSession(sessionId, e.getMessage());
            throw e;
        } finally {
            progress = ScanProgress.idle();
            scanning.set(false);
            ws.broadcast("SCAN_PROGRESS", progress);
        }
    }

    private ScanResult doScan(ScanMode mode, long sessionId, Instant started) {
        NetworkConfiguration cfg = networkInfo.configuration();
        if (cfg.ipv4() == null) {
            if (!interfaceWasDown) {
                interfaceWasDown = true;
                events.emit("NETWORK_INTERFACE_DOWN", "CRITICAL", null, null, cfg.hostname(),
                        "No active IPv4 network interface on the monitoring computer.");
            }
            throw new IllegalStateException("No active IPv4 network interface. Connect to the network and try again.");
        }
        interfaceWasDown = false;
        persistence.saveInterfaces(networkInfo.listInterfaces());

        List<Cidr> scopes = scopeService.resolveScopes();          // throws ScopeViolationException if not allowed
        String localIp = cfg.ipv4();
        String gwIp = cfg.gatewayIp();

        // ---- 1. build the target list (only authorized addresses) ----
        Set<String> targets = new LinkedHashSet<>();
        if (mode == ScanMode.FULL) {
            for (Cidr c : scopes) targets.addAll(c.hosts());
        } else {
            synchronized (registry) {
                for (String ip : registry.keySet()) if (ScopeService.inScope(scopes, ip)) targets.add(ip);
            }
        }
        if (gwIp != null) targets.add(gwIp);                       // our own next hop is always checked
        targets.remove(localIp);

        // ---- 2. ping sweep ----
        Map<String, PingResult> replies = sweep(mode, new ArrayList<>(targets), started);
        retryMissing(replies, targets);

        // ---- 3. ARP table ----
        setProgress(mode, "Reading neighbour table", targets.size(), targets.size(), started);
        Map<String, NeighborEntry> arp = neighbors.asMap();
        Set<String> arpOnly = new LinkedHashSet<>();
        for (NeighborEntry n : arp.values()) {
            if (!NeighborTableService.isFresh(n.state())) continue;
            if (n.ip().equals(localIp) || replies.containsKey(n.ip())) continue;
            if (ScopeService.inScope(scopes, n.ip()) || n.ip().equals(gwIp)) arpOnly.add(n.ip());
        }

        // ---- 4. names ----
        setProgress(mode, "Resolving names", targets.size(), targets.size(), started);
        Map<String, HostnameResolutionService.Resolved> names = resolveNames(replies, arpOnly);

        // ---- 5. merge + events ----
        setProgress(mode, "Analysing devices", targets.size(), targets.size(), started);
        Outcome outcome = merge(mode, cfg, scopes, targets, replies, arp, arpOnly, names);
        for (PendingEvent p : outcome.events()) events.emit(p.type(), p.severity(), p.ip(), p.mac(), p.hostname(), p.message());
        if (mode == ScanMode.FULL) baselineComplete = true;

        List<DeviceResponse> snapshot = devices();
        persistence.saveDevices(sessionId, snapshot, outcome.changedIps(), mode == ScanMode.FULL);

        int reachable = 0, arpCount = 0;
        for (DeviceResponse d : snapshot) {
            if ("REACHABLE".equals(d.state())) reachable++;
            if ("ARP_ONLY".equals(d.state())) arpCount++;
        }
        Instant finished = Instant.now();
        ScanResult result = new ScanResult(sessionId, mode.name(), started, finished,
                Duration.between(started, finished).toMillis(), scopes.stream().map(Cidr::toString).toList(),
                targets.size(), reachable, arpCount, snapshot.size(), outcome.newDevices(), outcome.wentOffline());
        if (mode == ScanMode.FULL) {
            events.emit("SCAN_COMPLETED", "INFO", null, null, null,
                    "Full scan finished in " + Math.max(1, result.durationMs() / 1000) + " s: " + reachable
                            + " reachable, " + arpCount + " ARP-only, " + targets.size() + " addresses probed in "
                            + String.join(", ", result.scopes()) + ".");
        }
        log.info("{} scan done: {} probed, {} reachable, {} ARP-only, {} known ({} ms)", mode, targets.size(), reachable,
                arpCount, snapshot.size(), result.durationMs());
        return result;
    }

    /** Pings every target with a bounded thread pool. Returns only the hosts that answered. */
    private Map<String, PingResult> sweep(ScanMode mode, List<String> targets, Instant started) {
        Map<String, PingResult> reachable = new ConcurrentHashMap<>();
        if (targets.isEmpty()) return reachable;
        int total = targets.size();
        int threads = Math.max(1, Math.min(config.scan().concurrency(), total));
        int timeout = config.scan().pingTimeoutMs();
        int delay = config.scan().delayBetweenProbesMs();
        ExecutorService pool = Executors.newFixedThreadPool(threads, NetworkUtils.daemonFactory("ping-sweep"));
        CountDownLatch latch = new CountDownLatch(total);
        AtomicInteger done = new AtomicInteger();

        for (String ip : targets) {
            pool.execute(() -> {
                try {
                    if (delay > 0) NetworkUtils.sleepQuietly(delay);
                    PingResult r = pingService.ping(ip, timeout);
                    if (r.reachable()) reachable.put(ip, r);
                } catch (Exception e) {
                    log.debug("Ping {} failed: {}", ip, e.getMessage());
                } finally {
                    done.incrementAndGet();
                    latch.countDown();
                }
            });
        }
        try {
            while (!latch.await(1500, TimeUnit.MILLISECONDS)) setProgress(mode, "Pinging addresses", done.get(), total, started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new IllegalStateException("Scan interrupted");
        }
        pool.shutdown();
        setProgress(mode, "Pinging addresses", total, total, started);
        return reachable;
    }

    /** A single lost packet must not make a healthy device look offline: re-ping known-good devices that missed. */
    private void retryMissing(Map<String, PingResult> replies, Set<String> targets) {
        List<String> missing = new ArrayList<>();
        synchronized (registry) {
            for (Device d : registry.values()) {
                boolean wasUp = "REACHABLE".equals(d.getState()) || "ARP_ONLY".equals(d.getState());
                if (!d.isLocal() && wasUp && targets.contains(d.getIp()) && !replies.containsKey(d.getIp())) missing.add(d.getIp());
            }
        }
        if (missing.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(16, missing.size()), NetworkUtils.daemonFactory("ping-retry"));
        List<Future<?>> fs = new ArrayList<>();
        for (String ip : missing) {
            fs.add(pool.submit(() -> {
                for (int i = 0; i < 2; i++) {
                    PingResult r = pingService.ping(ip, config.scan().pingTimeoutMs() * 2);
                    if (r.reachable()) {
                        replies.put(ip, r);
                        return;
                    }
                    NetworkUtils.sleepQuietly(300);
                }
            }));
        }
        for (Future<?> f : fs) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                f.cancel(true);
            }
        }
        pool.shutdown();
    }

    private Map<String, HostnameResolutionService.Resolved> resolveNames(Map<String, PingResult> replies, Set<String> arpOnly) {
        Map<String, CompletableFuture<HostnameResolutionService.Resolved>> futures = new LinkedHashMap<>();
        Instant recheckBefore = Instant.now().minus(Duration.ofHours(6));
        List<String> ips = new ArrayList<>(replies.keySet());
        ips.addAll(arpOnly);
        for (String ip : ips) {
            Device existing;
            synchronized (registry) {
                existing = registry.get(ip);
            }
            boolean need = existing == null || existing.getHostname() == null
                    || existing.getHostnameCheckedAt() == null || existing.getHostnameCheckedAt().isBefore(recheckBefore);
            if (existing != null && existing.getHostname() != null && !"history".equals(existing.getHostnameSource())
                    && existing.getHostnameCheckedAt() != null && existing.getHostnameCheckedAt().isAfter(recheckBefore)) need = false;
            if (!need) continue;
            PingResult pr = replies.get(ip);
            boolean windows = pr != null && DeviceIntelligenceService.ttlLooksWindows(pr.ttl());
            futures.put(ip, hostnames.resolveAsync(ip, windows));
        }
        Map<String, HostnameResolutionService.Resolved> out = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<HostnameResolutionService.Resolved>> e : futures.entrySet()) {
            try {
                HostnameResolutionService.Resolved r = e.getValue().join();
                out.put(e.getKey(), r);                              // null value = looked up, nothing found
            } catch (Exception ex) {
                out.put(e.getKey(), null);
            }
        }
        return out;
    }

    /** Applies scan results to the registry and works out which events to raise. Runs under the registry lock. */
    private Outcome merge(ScanMode mode, NetworkConfiguration cfg, List<Cidr> scopes, Set<String> targets, Map<String, PingResult> replies,
                          Map<String, NeighborEntry> arp, Set<String> arpOnly,
                          Map<String, HostnameResolutionService.Resolved> names) {
        Instant now = Instant.now();
        String localIp = cfg.ipv4();
        String gwIp = cfg.gatewayIp();
        int hist = config.health().historySize();
        List<PendingEvent> pending = new ArrayList<>();
        Set<String> changed = new HashSet<>();
        int newDevices = 0, wentOffline = 0;

        Set<String> alive = new LinkedHashSet<>(replies.keySet());
        alive.addAll(arpOnly);

        synchronized (registry) {
            // -- this computer --
            Device local = registry.computeIfAbsent(localIp, Device::new);
            if (local.getFirstSeen() == null) local.setFirstSeen(now);
            local.setLocal(true);
            local.setGateway(false);
            local.setLastSeen(now);
            local.setState("REACHABLE");
            local.setLatencyMs(0.0);
            local.setSource("LOCAL");
            local.setMac(cfg.mac());
            local.setSameSegment(true);
            local.setHostname(cfg.hostname());
            local.setHostnameSource("local");
            local.setSubnet(IpAddressUtils.subnet24(localIp));
            DeviceIntelligenceService.VendorInfo lv = intelligence.lookupVendor(cfg.mac());
            local.setVendor(lv.vendor());
            local.setVendorNote(lv.note());
            local.setOsHint(cfg.os());
            intelligence.classify(local);

            // Forget devices that belong to a network we are no longer connected to. They stay in the database
            // history, but the live view must only describe the network(s) inside the current authorized scope.
            int forgotten = 0;
            Iterator<Device> it = registry.values().iterator();
            while (it.hasNext()) {
                Device o = it.next();
                String oip = o.getIp();
                if (oip.equals(localIp) || oip.equals(gwIp)) continue;
                if (!ScopeService.inScope(scopes, oip)) {
                    it.remove();
                    forgotten++;
                }
            }
            if (forgotten > 0) log.info("Network changed: removed {} device(s) from the live view that are outside the current scope", forgotten);

            for (Device o : registry.values()) {
                if (!o.getIp().equals(localIp)) o.setLocal(false);
                o.setGateway(o.getIp().equals(gwIp));
                // a device that used to be "this computer" / "the gateway" but is not any more must be re-classified
                boolean staleLocal = "LOCAL_HOST".equals(o.getDeviceType()) && !o.isLocal();
                boolean staleGateway = "GATEWAY".equals(o.getDeviceType()) && !o.isGateway();
                if (staleLocal || staleGateway) intelligence.classify(o);
            }

            // gateway change
            if (gwIp != null && lastGatewayIp != null && !gwIp.equals(lastGatewayIp)) {
                pending.add(new PendingEvent("GATEWAY_CHANGED", "WARNING", gwIp, cfg.gatewayMac(), null,
                        "Default gateway changed from " + lastGatewayIp + " to " + gwIp
                                + ". The computer probably joined a different network."));
            }
            if (gwIp != null) lastGatewayIp = gwIp;

            // -- devices that answered ping or ARP --
            for (String ip : alive) {
                boolean icmp = replies.containsKey(ip);
                PingResult pr = replies.get(ip);
                Device d = registry.get(ip);
                boolean isNew = d == null;
                if (isNew) {
                    d = new Device(ip);
                    d.setFirstSeen(now);
                    registry.put(ip, d);
                }
                String prevState = d.getState();
                String prevMac = d.getMac();
                Instant prevLastSeen = d.getLastSeen();
                d.setGateway(ip.equals(gwIp));

                NeighborEntry n = arp.get(ip);
                String mac = n != null ? n.mac() : null;
                boolean ipChanged = false;
                if (mac != null) {
                    if (prevMac != null && !prevMac.equals(mac) && !"UNKNOWN".equals(prevState)) {
                        pending.add(new PendingEvent("MAC_CHANGED", "WARNING", ip, mac, d.getHostname(),
                                ip + " now answers with MAC " + mac + " (was " + prevMac + "). Possible IP conflict, "
                                        + "DHCP re-assignment or ARP spoofing - please verify."));
                    }
                    if (isNew) {
                        for (Device other : registry.values()) {
                            if (!other.getIp().equals(ip) && mac.equals(other.getMac())) {
                                pending.add(new PendingEvent("IP_CHANGED", "INFO", ip, mac, other.getHostname(),
                                        "Device with MAC " + mac + " moved from " + other.getIp() + " to " + ip + "."));
                                ipChanged = true;
                                break;
                            }
                        }
                    }
                    d.setMac(mac);
                    d.setSameSegment(true);
                }

                d.setLastSeen(now);
                d.setSubnet(IpAddressUtils.subnet24(ip));
                if (icmp) {
                    d.setState("REACHABLE");
                    d.setLatencyMs(pr.latencyMs());
                    d.setTtl(pr.ttl());
                    d.setOsHint(intelligence.osHint(pr.ttl()));
                    d.pushSample(pr.latencyMs(), hist);
                    d.setConsecutiveFailures(0);
                    d.setSource(mac != null ? "ICMP+ARP" : "ICMP");
                } else {
                    d.setState("ARP_ONLY");
                    d.setLatencyMs(null);
                    d.setConsecutiveFailures(0);
                    d.setSource("ARP");
                }

                if (names.containsKey(ip)) {
                    HostnameResolutionService.Resolved r = names.get(ip);
                    d.setHostnameCheckedAt(now);
                    if (r != null) {
                        d.setHostname(r.hostname());
                        d.setHostnameSource(r.source());
                    }
                }
                if (d.getVendor() == null && d.getVendorNote() == null || !Objects.equals(prevMac, d.getMac())) {
                    DeviceIntelligenceService.VendorInfo vi = intelligence.lookupVendor(d.getMac());
                    d.setVendor(vi.vendor());
                    d.setVendorNote(vi.note());
                }
                intelligence.classify(d);

                // latency band change
                String band = health.latencyBand(d.getLatencyMs());
                String prevBand = d.getLatencyBand();
                if (band != null && prevBand != null && !band.equals(prevBand) && "REACHABLE".equals(prevState)) {
                    boolean worse = rank(band) > rank(prevBand);
                    pending.add(new PendingEvent("LATENCY_CHANGED", worse ? "WARNING" : "INFO", ip, d.getMac(), d.getHostname(),
                            name(d) + " latency went from " + prevBand + " to " + band
                                    + String.format(" (now %.0f ms).", d.getLatencyMs())));
                }
                if (band != null) d.setLatencyBand(band);

                // state transitions
                String newState = d.getState();
                if (!newState.equals(prevState)) {
                    d.recordStateChange(now);
                    changed.add(ip);
                    if (isNew) {
                        newDevices++;
                        if (baselineComplete && !ipChanged) {
                            pending.add(new PendingEvent("DEVICE_NEW", "INFO", ip, d.getMac(), d.getHostname(),
                                    "New device " + name(d) + " appeared on " + d.getSubnet() + "."));
                        }
                    } else if ("UNREACHABLE".equals(prevState)) {
                        long offSec = prevLastSeen == null ? 0 : Duration.between(prevLastSeen, now).getSeconds();
                        boolean returned = offSec >= config.monitor().returnedAfterSeconds();
                        pending.add(new PendingEvent(returned ? "DEVICE_RETURNED" : "DEVICE_ONLINE", "INFO", ip, d.getMac(),
                                d.getHostname(), name(d) + (returned ? " returned after " + NetworkUtils.ago(prevLastSeen).replace(" ago", "")
                                + " offline." : " is back online.")));
                    }
                }
            }

            // -- known devices that did not answer this time --
            for (Device d : registry.values()) {
                if (d.isLocal() || alive.contains(d.getIp()) || !targets.contains(d.getIp())) continue;
                d.setConsecutiveFailures(d.getConsecutiveFailures() + 1);
                d.pushSample(null, hist);
                String prev = d.getState();
                if ("UNREACHABLE".equals(prev)) continue;
                d.setState("UNREACHABLE");
                d.setLatencyMs(null);
                d.setLatencyBand(null);
                if ("UNKNOWN".equals(prev)) continue;              // restored from history - not a real transition
                d.recordStateChange(now);
                changed.add(d.getIp());
                wentOffline++;
                boolean critical = d.isGateway() || "NETWORK_DEVICE".equals(d.getDeviceType());
                boolean mobile = "MOBILE".equals(d.getDeviceType());
                pending.add(new PendingEvent("DEVICE_OFFLINE", critical ? "CRITICAL" : mobile ? "INFO" : "WARNING", d.getIp(),
                        d.getMac(), d.getHostname(), name(d) + " stopped responding (last seen " + NetworkUtils.ago(d.getLastSeen()) + ")."));
            }

            for (Device d : registry.values()) health.analyze(d);
        }
        return new Outcome(pending, changed, newDevices, wentOffline);
    }

    private static int rank(String band) {
        return switch (band) {
            case "CRITICAL" -> 2;
            case "HIGH" -> 1;
            default -> 0;
        };
    }

    private static String name(Device d) {
        return d.getHostname() != null ? d.getHostname() + " (" + d.getIp() + ")" : d.getIp();
    }

    private void setProgress(ScanMode mode, String phase, int done, int total, Instant started) {
        progress = new ScanProgress(true, mode.name(), phase, done, total, started);
        ws.broadcast("SCAN_PROGRESS", progress);
    }
}
