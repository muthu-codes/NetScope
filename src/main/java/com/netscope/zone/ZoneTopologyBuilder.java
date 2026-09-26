package com.netscope.zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the multi-zone graph. Every edge says how we know it:
 *  OBSERVED   - measured directly (this host -> first gateway hop, or a directly connected LOCAL zone)
 *  INFERRED   - derived from traceroute hop order (Layer-3 path, NOT physical cabling)
 *  CONFIGURED - declared by an administrator (zone gateway)
 */
public final class ZoneTopologyBuilder {

    public record Node(String id, String label, String kind, String status, String detail) {
    }

    public record Edge(String from, String to, String type, String label) {
    }

    public record Graph(List<Node> nodes, List<Edge> edges, String disclaimer) {
    }

    private ZoneTopologyBuilder() {
    }

    public static Graph build(List<ZoneView> zones, String hostIp) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        nodes.put("host", new Node("host", "Monitoring host", "HOST", "UP", hostIp));

        for (ZoneView z : zones) {
            String zid = "zone:" + z.id();
            nodes.put(zid, new Node(zid, z.name(), "ZONE", z.status().name(), z.cidr() + " - " + z.up() + " up / " + z.down() + " down"));
            List<String> path = z.path() == null ? List.of() : z.path();

            if (z.type() == ZoneType.LOCAL || path.isEmpty()) {
                if (z.type() == ZoneType.LOCAL) addEdge(edges, "host", zid, "OBSERVED", "directly connected (Layer 2)");
                else if (z.gateway() != null && !z.gateway().isBlank()) {
                    String gid = "hop:" + z.gateway();
                    nodes.putIfAbsent(gid, new Node(gid, z.gateway(), "ROUTER", "UNKNOWN", "configured gateway"));
                    addEdge(edges, "host", gid, "CONFIGURED", "configured dependency");
                    addEdge(edges, gid, zid, "CONFIGURED", "configured gateway of " + z.name());
                }
                continue;
            }

            String prev = "host";
            for (int i = 0; i < path.size(); i++) {
                String ip = path.get(i);
                String hid = "hop:" + ip;
                nodes.putIfAbsent(hid, new Node(hid, ip, "ROUTER", "UP", i == 0 ? "observed gateway" : "observed route hop"));
                addEdge(edges, prev, hid, i == 0 ? "OBSERVED" : "INFERRED", i == 0 ? "observed gateway" : "inferred path");
                prev = hid;
            }
            addEdge(edges, prev, zid, "INFERRED", "inferred path");

            if (z.gateway() != null && !z.gateway().isBlank() && !path.contains(z.gateway())) {
                String gid = "hop:" + z.gateway();
                nodes.putIfAbsent(gid, new Node(gid, z.gateway(), "ROUTER", "UNKNOWN", "configured gateway"));
                addEdge(edges, zid, gid, "CONFIGURED", "configured dependency");
            }
        }
        return new Graph(new ArrayList<>(nodes.values()), edges,
                "Traceroute shows Layer-3 hops that answered from this host. It does not prove physical cabling or which switch a device is plugged into. "
                        + "That needs infrastructure data (SNMP/LLDP) or a manually declared topology.");
    }

    private static void addEdge(List<Edge> edges, String from, String to, String type, String label) {
        for (Edge e : edges) if (e.from().equals(from) && e.to().equals(to)) return;
        edges.add(new Edge(from, to, type, label));
    }
}
