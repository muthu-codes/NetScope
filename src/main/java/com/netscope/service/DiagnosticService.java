package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.dto.DeviceResponse;
import com.netscope.dto.DiagnosticFinding;
import com.netscope.dto.DiagnosticResponse;
import com.netscope.dto.DeviceStats;
import com.netscope.model.DiagnosticResult;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.PingStats;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Safe, read-only diagnostics: ping, latency, loss, jitter, DNS, gateway, internet, interface and a network-wide
 * roll-up. Nothing here changes any configuration or sends anything except ordinary ping / DNS / traceroute probes.
 */
@Service
public class DiagnosticService {

    private final PingService ping;
    private final NetworkInfoService networkInfo;
    private final DeviceDiscoveryService discovery;
    private final ScopeService scope;
    private final TracerouteService tracer;
    private final HostnameResolutionService hostnames;
    private final NetScopeConfig config;
    private final ExecutorService dnsPool = Executors.newFixedThreadPool(2, NetworkUtils.daemonFactory("diag-dns"));

    private volatile DiagnosticResponse lastNetwork;
    private volatile boolean internetOk;

    public DiagnosticService(PingService ping, NetworkInfoService networkInfo, DeviceDiscoveryService discovery,
                             ScopeService scope, TracerouteService tracer, HostnameResolutionService hostnames,
                             NetScopeConfig config) {
        this.ping = ping;
        this.networkInfo = networkInfo;
        this.discovery = discovery;
        this.scope = scope;
        this.tracer = tracer;
        this.hostnames = hostnames;
        this.config = config;
    }

    public Optional<DiagnosticResponse> last() {
        return Optional.ofNullable(lastNetwork);
    }

    public boolean internetReachable() {
        return internetOk;
    }

    /** Multi-packet ping to a device inside the authorized scope. */
    public PingStats ping(String ip, int count) {
        scope.assertInScope(ip);
        return ping.pingStats(ip, count, config.scan().pingTimeoutMs() + 400);
    }

    // ------------------------------------------------------------------ network-wide

    public synchronized DiagnosticResponse runNetworkDiagnostics() {
        List<DiagnosticResult> checks = new ArrayList<>();
        NetworkConfiguration cfg = networkInfo.configuration();

        // 1. local interface
        long t = System.currentTimeMillis();
        if (cfg.ipv4() == null) {
            checks.add(res("interface", "Local interface", "FAIL", "No active IPv4 interface",
                    "This computer has no usable network connection.", "Connect to Wi-Fi/Ethernet and check that the adapter is enabled.", t, null));
        } else if (cfg.ipv4().startsWith("169.254.")) {
            checks.add(res("interface", "Local interface", "FAIL", "No DHCP address (169.254.x.x)",
                    cfg.interfaceDisplayName() + " only has a self-assigned link-local address.",
                    "The DHCP server did not answer. Reconnect, check the cable/AP or the VLAN's DHCP scope.", t, null));
        } else {
            checks.add(res("interface", "Local interface", "PASS", cfg.interfaceType() + " up, " + cfg.ipv4() + "/" + cfg.prefixLength(),
                    cfg.interfaceDisplayName() + " on " + cfg.networkCidr() + ", MAC " + cfg.mac() + ".", null, t, null));
        }

        // 2 + 3. gateway
        String gw = cfg.gatewayIp();
        if (gw == null) {
            checks.add(res("gateway", "Default gateway", "FAIL", "No default gateway",
                    "The routing table has no default route, so nothing outside the local subnet is reachable.",
                    "Check DHCP settings or the static gateway configuration.", System.currentTimeMillis(), null));
        } else {
            t = System.currentTimeMillis();
            PingStats g = ping.pingStats(gw, 5, 1000);
            Optional<DeviceResponse> gwDev = discovery.gatewayDevice();
            boolean arpOnly = gwDev.isPresent() && "ARP_ONLY".equals(gwDev.get().state());
            if (g.received() == 0) {
                if (arpOnly) checks.add(res("gateway-reach", "Gateway reachability", "WARN", "Gateway " + gw + " is present but blocks ping",
                        "Its MAC answers ARP, so the router is up, but it ignores ICMP.", "Nothing to fix - many routers block ping.", t, null));
                else checks.add(res("gateway-reach", "Gateway reachability", "FAIL", "Gateway " + gw + " does not answer",
                        "5 of 5 pings were lost.", "Check the router power and the link between this computer and the router (cable, Wi-Fi signal).", t, null));
            } else if (g.lossPercent() > 0 || g.avgMs() > 50) {
                checks.add(res("gateway-reach", "Gateway reachability", "WARN",
                        String.format("Gateway answers but is unstable (%.0f%% loss, %.0f ms avg)", g.lossPercent(), g.avgMs()),
                        "Received " + g.received() + " of " + g.sent() + " replies; jitter " + g.jitterMs() + " ms.",
                        "A weak first hop hurts every client: check Wi-Fi signal, cabling and router load.", t, g.avgMs()));
            } else {
                checks.add(res("gateway-reach", "Gateway reachability", "PASS", String.format("Gateway %s answers in %.0f ms", gw, g.avgMs()),
                        "0% loss, jitter " + g.jitterMs() + " ms.", null, t, g.avgMs()));
            }
        }

        // 4. DNS server + 5. DNS resolution
        boolean dnsOk = false;
        if (!cfg.dnsServers().isEmpty()) {
            t = System.currentTimeMillis();
            String dns = cfg.dnsServers().get(0);
            PingStats d = ping.pingStats(dns, 3, 1000);
            checks.add(res("dns-server", "DNS server", d.received() > 0 ? "PASS" : "INFO",
                    d.received() > 0 ? "DNS server " + dns + " answers ping" : "DNS server " + dns + " ignores ping",
                    "Servers: " + String.join(", ", cfg.dnsServers()) + ".",
                    d.received() > 0 ? null : "DNS servers often block ICMP; the resolution test below is the real test.", t, d.avgMs()));
        }
        String host = config.diagnostics().dnsTestHost();
        if (host != null && !host.isBlank()) {
            t = System.currentTimeMillis();
            Future<String> f = dnsPool.submit(() -> InetAddress.getByName(host).getHostAddress());
            try {
                String addr = f.get(4, TimeUnit.SECONDS);
                long ms = System.currentTimeMillis() - t;
                dnsOk = true;
                checks.add(res("dns-resolve", "DNS resolution", ms >= 1000 ? "WARN" : "PASS",
                        host + " resolved to " + addr + " in " + ms + " ms", null,
                        ms >= 1000 ? "Slow DNS makes browsing feel slow. Try another DNS server or check the resolver's load." : null, t, (double) ms));
            } catch (Exception e) {
                f.cancel(true);
                checks.add(res("dns-resolve", "DNS resolution", "FAIL", "Could not resolve " + host,
                        "The lookup timed out or failed.", "Check that the DNS servers are reachable and that the network allows DNS.", t, null));
            }
        }

        // 6. internet
        String target = config.diagnostics().internetTarget();
        if (target != null && !target.isBlank()) {
            t = System.currentTimeMillis();
            PingStats p = ping.pingStats(target, 3, 1200);
            if (p.received() > 0) {
                internetOk = true;
                checks.add(res("internet", "Internet reachability", p.avgMs() > 200 ? "WARN" : "PASS",
                        String.format("%s answers in %.0f ms", target, p.avgMs()), "Loss " + p.lossPercent() + "%.",
                        p.avgMs() > 200 ? "High latency to the internet: check uplink utilisation and the ISP link." : null, t, p.avgMs()));
            } else if (dnsOk) {
                internetOk = true;
                checks.add(res("internet", "Internet reachability", "WARN", "ICMP to " + target + " is blocked, but DNS works",
                        "DNS resolved an external name, so the internet is reachable.", "Campus firewalls often block ICMP to the internet - not a fault.", t, null));
            } else {
                internetOk = false;
                checks.add(res("internet", "Internet reachability", "FAIL", "No answer from " + target, "Neither ping nor DNS worked.",
                        "If the gateway check passed, the fault is upstream: router WAN link, ISP or campus firewall.", t, null));
            }
        }

        // 7. coverage + 8. device health roll-up
        DeviceStats st = discovery.stats();
        List<DeviceResponse> devices = discovery.devices();
        checks.add(res("coverage", "Scan coverage", "INFO", st.total() + " devices in " + st.subnets() + " subnet(s)",
                st.reachable() + " answer ping, " + st.arpOnly() + " only answer ARP (ICMP blocked), " + st.unreachable() + " are offline.",
                "Devices behind firewalls, in sleep mode or on isolated VLANs/APs can be invisible - that is a limit of observation, not proof of absence.",
                System.currentTimeMillis(), null));
        String healthStatus = st.critical() > 0 ? "FAIL" : (st.degraded() > 0 || st.unreachable() > 0) ? "WARN" : "PASS";
        checks.add(res("device-health", "Device health", st.total() == 0 ? "INFO" : healthStatus,
                st.total() == 0 ? "No scan results yet" : st.healthy() + " healthy, " + st.degraded() + " degraded, " + st.critical() + " critical, " + st.unreachable() + " offline",
                st.avgLatencyMs() == null ? null : "Average latency " + st.avgLatencyMs() + " ms.",
                "Open the Devices page and sort by health to see each diagnosis.", System.currentTimeMillis(), st.avgLatencyMs()));

        // findings: worst devices first
        List<DeviceResponse> bad = new ArrayList<>();
        for (DeviceResponse d : devices) if (!d.local() && Set.of("CRITICAL", "DEGRADED", "OFFLINE").contains(d.healthStatus())) bad.add(d);
        bad.sort(Comparator.comparingInt((DeviceResponse d) -> priority(d)).thenComparingInt(DeviceResponse::healthScore));
        List<DiagnosticFinding> findings = new ArrayList<>();
        for (DeviceResponse d : bad) {
            if (findings.size() >= 12) break;
            findings.add(new DiagnosticFinding(d.ip(), d.displayName(), d.healthStatus(), d.healthScore(), d.diagnosis()));
        }

        DiagnosticResponse response = wrap("NETWORK", checks, findings, null, null);
        lastNetwork = response;
        return response;
    }

    private static int priority(DeviceResponse d) {
        if (d.gateway()) return 0;
        if ("NETWORK_DEVICE".equals(d.deviceType())) return 1;
        if ("CRITICAL".equals(d.healthStatus())) return 2;
        return 3;
    }

    // ------------------------------------------------------------------ one device

    public DiagnosticResponse diagnoseDevice(String ip, int count, boolean withTrace) {
        scope.assertInScope(ip);
        List<DiagnosticResult> checks = new ArrayList<>();
        Optional<DeviceResponse> known = discovery.device(ip);
        long t = System.currentTimeMillis();
        PingStats s = ping.pingStats(ip, count, config.scan().pingTimeoutMs() + 400);
        boolean arpOnly = known.isPresent() && "ARP_ONLY".equals(known.get().state());

        // reachability + loss
        if (s.received() == 0) {
            checks.add(res("reach", "Reachability", arpOnly ? "WARN" : "FAIL",
                    arpOnly ? "Blocks ping (but is present on the LAN)" : "No reply to any of " + s.sent() + " pings",
                    arpOnly ? "Its MAC is in the ARP table, so it is connected, but ICMP is filtered." : "The device did not answer.",
                    arpOnly ? "Normal for firewalled PCs; use another service check if you need availability." : "Check power, cable / Wi-Fi association and the switch port; confirm the device is not blocking ICMP.", t, null));
        } else {
            String st = s.lossPercent() >= config.health().lossCriticalPercent() ? "FAIL" : s.lossPercent() >= config.health().lossWarningPercent() ? "WARN" : "PASS";
            checks.add(res("loss", "Packet loss", st, String.format("%.0f%% loss (%d of %d replies)", s.lossPercent(), s.received(), s.sent()),
                    null, st.equals("PASS") ? null : "Check cabling/Wi-Fi interference or an overloaded link.", t, null));
            String ls = s.avgMs() >= config.health().criticalLatencyMs() ? "FAIL" : s.avgMs() >= config.health().highLatencyMs() ? "WARN" : "PASS";
            checks.add(res("latency", "Latency", ls, String.format("avg %.1f ms (min %.1f, max %.1f)", s.avgMs(), s.minMs(), s.maxMs()),
                    null, ls.equals("PASS") ? null : "High round-trip time: check link quality and load.", t, s.avgMs()));
            String js = s.jitterMs() >= config.health().jitterWarningMs() ? "WARN" : "PASS";
            checks.add(res("jitter", "Jitter", js, String.format("%.1f ms variation between replies", s.jitterMs()), null,
                    js.equals("PASS") ? null : "Unstable link: look for interference or bursty traffic.", t, null));
        }

        // name
        t = System.currentTimeMillis();
        HostnameResolutionService.Resolved name = hostnames.resolve(ip, true);
        checks.add(res("name", "Name resolution", name == null ? "INFO" : "PASS",
                name == null ? "No hostname found" : name.hostname() + " (" + name.source() + ")", null,
                name == null ? "Add a DNS/DHCP record if the device should be identifiable." : null, t, null));

        // identity from the registry
        known.ifPresent(d -> {
            checks.add(res("identity", "Identity", "INFO",
                    d.deviceType() + (d.vendor() != null ? " - " + d.vendor() : ""),
                    (d.vendorNote() == null ? "" : d.vendorNote() + " ") + (d.classificationReason() == null ? "" : "Type: " + d.classificationReason())
                            + (d.osHint() == null ? "" : " OS hint: " + d.osHint()), null, System.currentTimeMillis(), null));
            checks.add(res("history", "Recent history", "INFO", d.healthStatus() + " (score " + d.healthScore() + ")", d.diagnosis(), null,
                    System.currentTimeMillis(), d.avgLatencyMs()));
        });

        List<String> path = null;
        if (withTrace) {
            TracerouteService.TraceResult tr = tracer.trace(ip);
            path = new ArrayList<>();
            for (TracerouteService.Hop h : tr.hops())
                path.add(h.index() + "  " + (h.ip() == null ? "* (no reply)" : h.ip()) + (h.rttMs() == null ? "" : String.format("  %.0f ms", h.rttMs())));
        }
        return wrap(ip, checks, List.of(), s, path);
    }

    // ------------------------------------------------------------------ helpers

    private DiagnosticResponse wrap(String target, List<DiagnosticResult> checks, List<DiagnosticFinding> findings,
                                    PingStats stats, List<String> path) {
        long fail = checks.stream().filter(c -> "FAIL".equals(c.status())).count();
        long warn = checks.stream().filter(c -> "WARN".equals(c.status())).count();
        long pass = checks.stream().filter(c -> "PASS".equals(c.status())).count();
        String overall = fail > 0 ? "CRITICAL" : warn > 0 ? "DEGRADED" : "HEALTHY";
        String summary = fail > 0 ? fail + " check(s) failed and " + warn + " need attention."
                : warn > 0 ? warn + " check(s) need attention, " + pass + " passed."
                : "All " + pass + " checks passed.";
        return new DiagnosticResponse(target, Instant.now(), overall, summary, checks, findings, stats, path);
    }

    private static DiagnosticResult res(String id, String name, String status, String summary, String detail,
                                        String recommendation, long startedMs, Double latency) {
        return new DiagnosticResult(id, name, status, summary, detail, recommendation, System.currentTimeMillis() - startedMs, latency);
    }
}
