package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.dto.TopologyResponse;
import com.netscope.model.MonitoringStatus;
import com.netscope.model.ScanMode;
import com.netscope.model.ScanProgress;
import com.netscope.model.ScanResult;
import com.netscope.util.NetworkUtils;
import com.netscope.websocket.NetworkEventWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates monitoring:  scan -> trace routes -> diagnostics -> topology -> notify dashboards.
 * The scheduler calls tick() every few seconds; tick() decides whether a FULL sweep or a quick REFRESH is due.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final NetScopeConfig config;
    private final DeviceDiscoveryService discovery;
    private final TracerouteService tracer;
    private final DiagnosticService diagnostics;
    private final TopologyService topology;
    private final PersistenceService persistence;
    private final NetworkEventWebSocketHandler ws;

    private final AtomicBoolean enabled;
    private final ExecutorService manual = Executors.newSingleThreadExecutor(NetworkUtils.daemonFactory("manual-scan"));

    private volatile Instant lastFull, lastRefresh, lastAttempt, lastPurge = Instant.now();
    private volatile Long lastDurationMs;
    private volatile String lastError;
    private volatile ScanResult lastResult;
    private volatile String enrichPhase;

    public MonitoringService(NetScopeConfig config, DeviceDiscoveryService discovery, TracerouteService tracer,
                             DiagnosticService diagnostics, TopologyService topology, PersistenceService persistence,
                             NetworkEventWebSocketHandler ws) {
        this.config = config;
        this.discovery = discovery;
        this.tracer = tracer;
        this.diagnostics = diagnostics;
        this.topology = topology;
        this.persistence = persistence;
        this.ws = ws;
        this.enabled = new AtomicBoolean(config.monitor().enabled());
    }

    public void start() {
        enabled.set(true);
    }

    public void stop() {
        enabled.set(false);
    }

    public boolean isBusy() {
        return discovery.isScanning() || enrichPhase != null;
    }

    /** Called by the scheduler. Never throws. */
    public void tick() {
        if (!enabled.get() || isBusy()) return;
        Instant now = Instant.now();
        if (lastError != null && lastAttempt != null && Duration.between(lastAttempt, now).getSeconds() < 60) return;   // back off after errors

        try {
            if (Duration.between(lastPurge, now).toHours() >= 6) {
                lastPurge = now;
                persistence.purgeOld();
            }
            boolean fullDue = lastFull == null || Duration.between(lastFull, now).getSeconds() >= config.monitor().fullScanIntervalSeconds();
            if (fullDue) {
                runCycle(ScanMode.FULL);
                return;
            }
            Instant lastAny = lastRefresh != null && lastRefresh.isAfter(lastFull) ? lastRefresh : lastFull;
            if (Duration.between(lastAny, now).getSeconds() >= config.monitor().refreshIntervalSeconds()) runCycle(ScanMode.REFRESH);
        } catch (Exception e) {
            log.warn("Monitoring cycle failed: {}", e.getMessage());
        }
    }

    /** Starts a scan in the background. Returns false if one is already running. */
    public boolean triggerAsync(ScanMode mode) {
        if (isBusy()) return false;
        manual.submit(() -> {
            try {
                runCycle(mode);
            } catch (Exception e) {
                log.warn("Manual {} scan failed: {}", mode, e.getMessage());
            }
        });
        return true;
    }

    public ScanResult runCycle(ScanMode mode) {
        lastAttempt = Instant.now();
        try {
            ScanResult r = discovery.runScan(mode);
            lastResult = r;
            lastDurationMs = r.durationMs();
            lastError = null;
            if (mode == ScanMode.FULL) lastFull = r.finishedAt();
            else lastRefresh = r.finishedAt();

            try {
                if (mode == ScanMode.FULL) {
                    enrichPhase = "Tracing routes";
                    ws.broadcast("SCAN_PROGRESS", new ScanProgress(true, mode.name(), enrichPhase, 0, 0, r.startedAt()));
                    tracer.refresh(discovery.devices());
                    enrichPhase = "Running diagnostics";
                    ws.broadcast("SCAN_PROGRESS", new ScanProgress(true, mode.name(), enrichPhase, 0, 0, r.startedAt()));
                    diagnostics.runNetworkDiagnostics();
                }
                TopologyResponse topo = topology.build();
                if (mode == ScanMode.FULL) persistence.saveTopology(r.sessionId(), topo);
            } catch (Exception e) {
                log.warn("Post-scan enrichment failed: {}", e.getMessage());
            } finally {
                enrichPhase = null;
                ws.broadcast("SCAN_PROGRESS", ScanProgress.idle());
            }
            ws.broadcast("SCAN_COMPLETE", r);
            return r;
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            log.warn("{} scan refused/failed: {}", mode, e.getMessage());
            ws.broadcast("SCAN_ERROR", Map.of("message", String.valueOf(e.getMessage())));
            throw e;
        }
    }

    public MonitoringStatus status() {
        ScanProgress p = discovery.progress();
        boolean running = p.running() || enrichPhase != null;
        String phase = p.running() ? p.phase() : enrichPhase != null ? enrichPhase : "Idle";
        Instant next = lastFull == null ? null : lastFull.plusSeconds(config.monitor().fullScanIntervalSeconds());
        return new MonitoringStatus(enabled.get(), running, phase, p.done(), p.total(), p.mode(), p.startedAt(),
                lastFull, lastRefresh, lastDurationMs, next, config.monitor().fullScanIntervalSeconds(),
                config.monitor().refreshIntervalSeconds(), lastError, lastResult, discovery.baselineComplete());
    }
}
