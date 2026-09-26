package com.netscope.service;

import com.netscope.dto.DeviceResponse;
import com.netscope.dto.SubnetSummary;
import com.netscope.dto.TopologyResponse;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.TopologyEdge;
import com.netscope.model.TopologyNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Builds the topology graph ONLY from real observations:
 *  - local host -> gateway              : routing table (OBSERVED)
 *  - gateway -> subnet on same LAN      : devices' MACs seen in ARP => same Layer-2 segment (OBSERVED)
 *  - routed subnets                     : hop list from traceroute (INFERRED L3 path); if no trace exists the link to
 *                                         the gateway is drawn as ASSUMED and labelled so
 *  - device -> subnet hub               : membership by IP address (INFERRED) or ARP (OBSERVED)
 * It never invents switches, cables or access points.
 */
@Service
public class TopologyService {

    private final DeviceDiscoveryService discovery;
    private final TracerouteService tracer;
    private final DiagnosticService diagnostics;
    private final NetworkInfoService networkInfo;

    public TopologyService(DeviceDiscoveryService discovery, TracerouteService tracer, DiagnosticService diagnostics,
                           NetworkInfoService networkInfo) {
        this.discovery = discovery;
        this.tracer = tracer;
        this.diagnostics = diagnostics;
        this.networkInfo = networkInfo;
    }

    public TopologyResponse build() {
        NetworkConfiguration cfg = networkInfo.configuration();
        List<DeviceResponse> devices = discovery.devices();
        Map<String, SubnetSummary> subnets = new LinkedHashMap<>();
        for (SubnetSummary s : discovery.subnets()) subnets.put(s.subnet(), s);
        Map<String, TracerouteService.TraceResult> traces = new HashMap<>();
        for (TracerouteService.TraceResult r : tracer.results()) traces.put(r.subnet(), r);
        Set<String> hopIps = tracer.hopIps();

        Map<String, TopologyNode> nodes = new LinkedHashMap<>();
        Map<String, TopologyEdge> edges = new LinkedHashMap<>();
        String localId = null, gwId = null;
        Set<String> l2Subnets = new HashSet<>();

        // ---- device nodes ----
        for (DeviceResponse d : devices) {
            String type = d.local() ? "LOCAL_HOST" : d.gateway() ? "GATEWAY" : hopIps.contains(d.ip()) ? "ROUTER" : d.deviceType();
            nodes.put(d.ip(), new TopologyNode(d.ip(), d.displayName(), type, d.ip(), d.mac(), d.vendor(), d.state(),
                    d.latencyMs(), d.healthStatus(), d.healthScore(), d.local() ? "local interface" : d.gateway() ? "routing table + scan" : "scan",
                    d.subnet(), null, null, false));
            if (d.local()) localId = d.ip();
            if (d.gateway()) gwId = d.ip();
            if (d.sameSegment() && d.subnet() != null) l2Subnets.add(d.subnet());
        }

        // ---- subnet hubs ----
        for (SubnetSummary s : subnets.values()) {
            String id = "subnet:" + s.subnet();
            nodes.put(id, new TopologyNode(id, s.subnet(), "SUBNET", null, null, null, s.online() > 0 ? "REACHABLE" : "UNREACHABLE",
                    s.avgLatencyMs(), s.worstHealth(), null, "ip-subnet grouping", s.subnet(), s.total(), s.online(), false));
        }

        // ---- local -> gateway ----
        if (localId != null && gwId != null)
            addEdge(edges, localId, gwId, "default route", "ROUTING_TABLE", "OBSERVED", "default route");

        // ---- devices -> hubs ----
        for (DeviceResponse d : devices) {
            if (d.local() || d.gateway() || d.subnet() == null) continue;
            boolean l2 = d.sameSegment();
            addEdge(edges, d.ip(), "subnet:" + d.subnet(), "member of subnet", l2 ? "ARP_OBSERVED" : "IP_SUBNET_INFERRED",
                    l2 ? "OBSERVED" : "INFERRED", null);
        }

        // ---- hubs -> gateway / routed path ----
        String root = gwId != null ? gwId : localId;
        int assumed = 0;
        for (SubnetSummary s : subnets.values()) {
            String hub = "subnet:" + s.subnet();
            boolean l2 = l2Subnets.contains(s.subnet());
            if (root == null) continue;
            if (l2) {
                addEdge(edges, root, hub, "same Layer-2 segment", "ARP_OBSERVED", "OBSERVED", "same LAN");
                continue;
            }
            TracerouteService.TraceResult tr = traces.get(s.subnet());
            List<TracerouteService.Hop> hops = new ArrayList<>();
            if (tr != null) {
                for (TracerouteService.Hop h : tr.hops()) if (!tr.target().equals(h.ip())) hops.add(h);
            }
            String cursor = localId != null ? localId : root;
            boolean any = false;
            for (TracerouteService.Hop h : hops) {
                String id = hopNode(nodes, h, s.subnet(), gwId);
                if (!id.equals(cursor)) addEdge(edges, cursor, id, "routed hop", "TRACEROUTE", "INFERRED",
                        h.rttMs() == null ? "hop " + h.index() : String.format("hop %d, %.0f ms", h.index(), h.rttMs()));
                cursor = id;
                any = true;
            }
            if (any) {
                addEdge(edges, cursor, hub, "routes this subnet", "TRACEROUTE", "INFERRED", "L3 path");
            } else {
                addEdge(edges, root, hub, "routed via default gateway (assumed)", "ASSUMED_VIA_GATEWAY", "ASSUMED", "assumed");
                assumed++;
            }
        }

        // ---- internet (drawn only when a live probe reached it) ----
        if (gwId != null && diagnostics.internetReachable()) {
            nodes.put("internet", new TopologyNode("internet", "Internet", "INTERNET", null, null, null, "REACHABLE", null,
                    "HEALTHY", null, "diagnostic probe", null, null, null, true));
            addEdge(edges, gwId, "internet", "upstream", "CONCEPTUAL", "CONCEPTUAL", "conceptual");
        }

        // ---- summary + notes ----
        Map<String, Object> summary = new LinkedHashMap<>();
        long deviceNodes = nodes.values().stream().filter(n -> n.subnet() != null && !"SUBNET".equals(n.type())).count();
        summary.put("devices", deviceNodes);
        summary.put("subnets", subnets.size());
        summary.put("routers", nodes.values().stream().filter(n -> "ROUTER".equals(n.type())).count());
        summary.put("tracedSubnets", traces.size());
        summary.put("edgesObserved", edges.values().stream().filter(e -> "OBSERVED".equals(e.confidence())).count());
        summary.put("edgesInferred", edges.values().stream().filter(e -> "INFERRED".equals(e.confidence())).count());
        summary.put("edgesAssumed", edges.values().stream().filter(e -> "ASSUMED".equals(e.confidence())).count());

        List<String> notes = new ArrayList<>();
        notes.add("Every relationship comes from live data: routing table, ARP table, traceroute hops or IP addressing.");
        notes.add("Physical switch, access-point and cable connections are NOT shown - no SNMP/LLDP management data is configured.");
        if (assumed > 0)
            notes.add(assumed + " routed subnet(s) have no traceroute data yet; their link to the gateway is drawn dotted because it is assumed.");
        if (devices.isEmpty()) notes.add("No scan has completed yet.");

        return new TopologyResponse(Instant.now(), "OBSERVED_INFERRED",
                "Observed / inferred topology. NetScope draws only relationships supported by live data (ARP, routing table, "
                        + "traceroute, IP addressing). It does not show physical switch or cable connections.",
                new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()), summary, notes);
    }

    private String hopNode(Map<String, TopologyNode> nodes, TracerouteService.Hop h, String subnet, String gwId) {
        if (h.ip() == null) {
            String id = "hop:" + subnet + ":" + h.index();
            nodes.putIfAbsent(id, new TopologyNode(id, "Hop " + h.index() + " (no reply)", "ROUTER", null, null, null, "UNKNOWN", null,
                    "UNKNOWN", null, "traceroute", null, null, null, false));
            return id;
        }
        if (h.ip().equals(gwId) && nodes.containsKey(gwId)) return gwId;
        if (nodes.containsKey(h.ip())) return h.ip();
        String id = "hop:" + h.ip();
        nodes.putIfAbsent(id, new TopologyNode(id, h.ip(), "ROUTER", h.ip(), null, null, "REACHABLE", h.rttMs(), "UNKNOWN", null,
                "traceroute", null, null, null, false));
        return id;
    }

    private static void addEdge(Map<String, TopologyEdge> edges, String from, String to, String relation, String evidence,
                                String confidence, String label) {
        String id = from + "->" + to;
        edges.putIfAbsent(id, new TopologyEdge(id, from, to, relation, evidence, confidence, label));
    }
}
