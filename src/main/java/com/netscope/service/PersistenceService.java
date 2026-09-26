package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.dto.DeviceResponse;
import com.netscope.dto.ObservationPoint;
import com.netscope.dto.TopologyResponse;
import com.netscope.entity.*;
import com.netscope.model.Device;
import com.netscope.model.NetworkEvent;
import com.netscope.model.NetworkInterfaceInfo;
import com.netscope.model.ScanResult;
import com.netscope.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Everything that touches the database. Persistence is BEST-EFFORT: if the database is down the live scanner,
 * topology and dashboard keep working - errors are only logged.
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final DeviceRepository devices;
    private final ObservationRepository observations;
    private final NetworkEventRepository events;
    private final ScanSessionRepository sessions;
    private final NetworkInterfaceRepository interfaces;
    private final TopologyNodeRepository topoNodes;
    private final TopologyEdgeRepository topoEdges;
    private final NetScopeConfig config;
    private final TransactionTemplate tx;

    public PersistenceService(DeviceRepository devices, ObservationRepository observations, NetworkEventRepository events,
                              ScanSessionRepository sessions, NetworkInterfaceRepository interfaces,
                              TopologyNodeRepository topoNodes, TopologyEdgeRepository topoEdges,
                              NetScopeConfig config, PlatformTransactionManager txManager) {
        this.devices = devices;
        this.observations = observations;
        this.events = events;
        this.sessions = sessions;
        this.interfaces = interfaces;
        this.topoNodes = topoNodes;
        this.topoEdges = topoEdges;
        this.config = config;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------------- scan sessions ----------------

    public long startSession(String mode) {
        try {
            ScanSessionEntity s = new ScanSessionEntity();
            s.setScanMode(mode);
            s.setStartedAt(Instant.now());
            s.setStatus("RUNNING");
            return sessions.save(s).getId();
        } catch (Exception e) {
            log.warn("Could not save scan session: {}", e.getMessage());
            return 0;
        }
    }

    public void finishSession(long id, ScanResult r) {
        if (id == 0) return;
        try {
            sessions.findById(id).ifPresent(s -> {
                s.setFinishedAt(r.finishedAt());
                s.setStatus("DONE");
                s.setTargetsProbed(r.targetsProbed());
                s.setDevicesReachable(r.devicesReachable());
                s.setDevicesTotal(r.devicesTotal());
                s.setScopes(String.join(",", r.scopes()));
                sessions.save(s);
            });
        } catch (Exception e) {
            log.warn("Could not finish scan session: {}", e.getMessage());
        }
    }

    public void failSession(long id, String message) {
        if (id == 0) return;
        try {
            sessions.findById(id).ifPresent(s -> {
                s.setFinishedAt(Instant.now());
                s.setStatus("FAILED");
                s.setError(message == null ? null : message.substring(0, Math.min(490, message.length())));
                sessions.save(s);
            });
        } catch (Exception e) {
            log.warn("Could not mark scan session failed: {}", e.getMessage());
        }
    }

    // ---------------- devices + observations ----------------

    /**
     * Upserts every device and stores one observation per device. To keep the database small,
     * observations are written on FULL scans, and on REFRESH scans only for devices whose state changed.
     */
    public void saveDevices(long sessionId, List<DeviceResponse> list, Set<String> changedIps, boolean fullScan) {
        try {
            Map<String, DeviceEntity> existing = new HashMap<>();
            for (DeviceEntity e : devices.findAll()) existing.put(e.getIp(), e);

            List<DeviceEntity> toSave = new ArrayList<>();
            for (DeviceResponse d : list) {
                DeviceEntity e = existing.get(d.ip());
                if (e == null) {
                    e = new DeviceEntity();
                    e.setIp(d.ip());
                    e.setFirstSeen(d.firstSeen() != null ? d.firstSeen() : Instant.now());
                }
                e.setMac(d.mac());
                e.setHostname(d.hostname());
                e.setVendor(d.vendor());
                e.setDeviceType(d.deviceType());
                e.setState(d.state());
                e.setSubnet(d.subnet());
                e.setLastSeen(d.lastSeen());
                e.setLastLatencyMs(d.latencyMs());
                toSave.add(e);
            }
            List<DeviceEntity> saved = devices.saveAll(toSave);
            Map<String, Long> ids = new HashMap<>();
            for (DeviceEntity e : saved) ids.put(e.getIp(), e.getId());

            Instant now = Instant.now();
            List<ObservationEntity> obs = new ArrayList<>();
            for (DeviceResponse d : list) {
                if (d.local() || "UNKNOWN".equals(d.state())) continue;
                if (!fullScan && !changedIps.contains(d.ip())) continue;
                ObservationEntity o = new ObservationEntity();
                o.setDeviceId(ids.get(d.ip()));
                o.setSessionId(sessionId == 0 ? null : sessionId);
                o.setObservedAt(now);
                o.setReachable("REACHABLE".equals(d.state()) || "ARP_ONLY".equals(d.state()));
                o.setLatencyMs(d.latencyMs());
                o.setTtl(d.ttl());
                obs.add(o);
            }
            observations.saveAll(obs);
        } catch (Exception e) {
            log.warn("Could not save devices/observations: {}", e.getMessage());
        }
    }

    public List<Device> loadKnownDevices() {
        List<Device> out = new ArrayList<>();
        try {
            for (DeviceEntity e : devices.findAll()) {
                Device d = new Device(e.getIp());
                d.setMac(e.getMac());
                d.setHostname(e.getHostname());
                d.setHostnameSource(e.getHostname() == null ? null : "history");
                d.setVendor(e.getVendor());
                d.setDeviceType(e.getDeviceType() == null ? "UNKNOWN" : e.getDeviceType());
                d.setSubnet(e.getSubnet());
                d.setFirstSeen(e.getFirstSeen());
                d.setLastSeen(e.getLastSeen());
                d.setState("UNKNOWN");
                out.add(d);
            }
        } catch (Exception e) {
            log.warn("Could not load known devices: {}", e.getMessage());
        }
        return out;
    }

    public List<ObservationPoint> history(String ip) {
        try {
            Optional<DeviceEntity> dev = devices.findByIp(ip);
            if (dev.isEmpty()) return List.of();
            List<ObservationEntity> rows = new ArrayList<>(observations.findTop200ByDeviceIdOrderByObservedAtDesc(dev.get().getId()));
            Collections.reverse(rows);
            List<ObservationPoint> out = new ArrayList<>();
            for (ObservationEntity o : rows) out.add(new ObservationPoint(o.getObservedAt(), o.isReachable(), o.getLatencyMs()));
            return out;
        } catch (Exception e) {
            log.warn("Could not read history for {}: {}", ip, e.getMessage());
            return List.of();
        }
    }

    // ---------------- events ----------------

    public void saveEvent(NetworkEvent ev) {
        try {
            NetworkEventEntity e = new NetworkEventEntity();
            e.setId(ev.id());
            e.setOccurredAt(ev.timestamp());
            e.setEventType(ev.type());
            e.setSeverity(ev.severity());
            e.setIp(ev.ip());
            e.setMac(ev.mac());
            e.setHostname(ev.hostname());
            String m = ev.message();
            e.setMessage(m != null && m.length() > 690 ? m.substring(0, 690) : m);
            events.save(e);
        } catch (Exception ex) {
            log.warn("Could not save event: {}", ex.getMessage());
        }
    }

    /** Newest first. */
    public List<NetworkEvent> loadRecentEvents() {
        List<NetworkEvent> out = new ArrayList<>();
        try {
            for (NetworkEventEntity e : events.findTop500ByOrderByOccurredAtDesc())
                out.add(new NetworkEvent(e.getId(), e.getOccurredAt(), e.getEventType(), e.getSeverity(), e.getIp(),
                        e.getMac(), e.getHostname(), e.getMessage()));
        } catch (Exception e) {
            log.warn("Could not load events: {}", e.getMessage());
        }
        return out;
    }

    // ---------------- topology + interfaces ----------------

    public void saveTopology(long sessionId, TopologyResponse topo) {
        try {
            Instant at = topo.generatedAt();
            List<TopologyNodeEntity> nodes = new ArrayList<>();
            topo.nodes().forEach(n -> {
                TopologyNodeEntity e = new TopologyNodeEntity();
                e.setSessionId(sessionId == 0 ? null : sessionId);
                e.setCapturedAt(at);
                e.setNodeKey(n.id());
                e.setLabel(n.label());
                e.setNodeType(n.type());
                e.setIp(n.ip());
                e.setMac(n.mac());
                e.setState(n.state());
                nodes.add(e);
            });
            List<TopologyEdgeEntity> edges = new ArrayList<>();
            topo.edges().forEach(x -> {
                TopologyEdgeEntity e = new TopologyEdgeEntity();
                e.setSessionId(sessionId == 0 ? null : sessionId);
                e.setCapturedAt(at);
                e.setSourceKey(x.source());
                e.setTargetKey(x.target());
                e.setRelation(x.relation());
                e.setEvidence(x.evidence());
                e.setConfidence(x.confidence());
                edges.add(e);
            });
            topoNodes.saveAll(nodes);
            topoEdges.saveAll(edges);
        } catch (Exception e) {
            log.warn("Could not save topology snapshot: {}", e.getMessage());
        }
    }

    public void saveInterfaces(List<NetworkInterfaceInfo> list) {
        try {
            for (NetworkInterfaceInfo i : list) {
                if (i.loopback()) continue;
                NetworkInterfaceEntity e = interfaces.findByName(i.name()).orElseGet(NetworkInterfaceEntity::new);
                e.setName(i.name());
                e.setDisplayName(i.displayName());
                e.setInterfaceType(i.type());
                e.setMac(i.mac());
                e.setIpv4(String.join(",", i.ipv4()));
                e.setUp(i.up());
                e.setLastSeen(Instant.now());
                interfaces.save(e);
            }
        } catch (Exception e) {
            log.warn("Could not save interfaces: {}", e.getMessage());
        }
    }

    /** Deletes data older than the retention window. */
    public void purgeOld() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, config.retention().observationDays())));
            tx.executeWithoutResult(status -> {
                long o = observations.deleteByObservedAtBefore(cutoff);
                long n = topoNodes.deleteByCapturedAtBefore(cutoff);
                long e = topoEdges.deleteByCapturedAtBefore(cutoff);
                long ev = events.deleteByOccurredAtBefore(cutoff.minus(Duration.ofDays(23)));
                if (o + n + e + ev > 0) log.info("Retention purge removed {} observations, {} nodes, {} edges, {} old events", o, n, e, ev);
            });
        } catch (Exception e) {
            log.warn("Retention purge failed: {}", e.getMessage());
        }
    }
}
