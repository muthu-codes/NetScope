package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.model.Device;
import com.netscope.model.Issue;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw measurements into: health score, health status, a list of issues and a written diagnosis.
 * All rules are simple and explainable - every issue says WHAT was measured, WHY it matters and what to CHECK.
 */
@Service
public class HealthAnalysisService {

    private final NetScopeConfig config;

    public HealthAnalysisService(NetScopeConfig config) {
        this.config = config;
    }

    public String latencyBand(Double ms) {
        if (ms == null) return null;
        if (ms >= config.health().criticalLatencyMs()) return "CRITICAL";
        if (ms >= config.health().highLatencyMs()) return "HIGH";
        return "OK";
    }

    public void analyze(Device d) {
        computeStats(d);
        if (d.isLocal()) {
            d.setIssues(new ArrayList<>());
            d.setHealthScore(100);
            d.setHealthStatus("HEALTHY");
            d.setDiagnosis("This is the computer running NetScope (the monitoring host).");
            return;
        }
        List<Issue> issues = new ArrayList<>();
        int score = 100;
        String state = d.getState();
        String who = d.getHostname() != null ? d.getHostname() + " (" + d.getIp() + ")" : d.getIp();

        if ("UNKNOWN".equals(state)) {
            d.setIssues(issues);
            d.setHealthScore(0);
            d.setHealthStatus("UNKNOWN");
            d.setDiagnosis("Restored from history. Waiting for the first scan to confirm whether it is online.");
            return;
        }

        if ("UNREACHABLE".equals(state)) {
            boolean critical = d.isGateway() || "NETWORK_DEVICE".equals(d.getDeviceType());
            String reco;
            if (d.isGateway())
                reco = "The default gateway is not answering, so every device behind it loses connectivity. Check the router's power, its uplink cable and the upstream switch port.";
            else if ("NETWORK_DEVICE".equals(d.getDeviceType()))
                reco = "Infrastructure device is down. Check its power/PoE, its uplink cable and the upstream switch port; devices behind it may be affected.";
            else if ("MOBILE".equals(d.getDeviceType()))
                reco = "Phones often leave Wi-Fi or sleep, so this is usually not a fault.";
            else
                reco = "The device may be powered off, asleep, unplugged, moved to another network, or blocking ICMP. If it should be online, check power, the cable/Wi-Fi association and its switch port.";
            String detail = "Last answered " + NetworkUtils.ago(d.getLastSeen()) + "; " + Math.max(1, d.getConsecutiveFailures())
                    + " probe cycle(s) in a row got no reply.";
            issues.add(new Issue("UNREACHABLE", critical ? "CRITICAL" : "WARNING", "No response to ping", detail, reco));
            d.setIssues(issues);
            d.setHealthScore(0);
            d.setHealthStatus("OFFLINE");
            d.setDiagnosis("Offline. " + detail + " " + reco);
            return;
        }

        if ("ARP_ONLY".equals(state)) {
            issues.add(new Issue("ICMP_BLOCKED", "INFO", "Answers ARP but not ping",
                    "The device's MAC answered our ARP request, so it is on the LAN, but it ignores ICMP echo.",
                    "This is normal for Windows machines with the default firewall. Latency cannot be measured for it."));
            if (d.getHostname() == null)
                issues.add(new Issue("NO_NAME", "INFO", "No hostname found", "Reverse DNS and NetBIOS gave no name.",
                        "Add a DNS/DHCP record for the device if it should be identifiable."));
            d.setIssues(issues);
            d.setHealthScore(70);
            d.setHealthStatus("LIMITED");
            d.setDiagnosis("Present on the network (answers ARP) but blocks ping, so latency and loss are unknown. This is common for firewalled PCs.");
            return;
        }

        // ---- REACHABLE ----
        Double lat = d.getLatencyMs();
        Double avg = d.getAvgLatencyMs();
        double measured = lat != null ? Math.max(lat, avg == null ? 0 : avg) : (avg == null ? 0 : avg);
        boolean routed = !d.isSameSegment() && !d.isLocal();
        String pathNote = routed ? " The path crosses one or more routers, so the value includes every hop." : "";

        if (measured >= config.health().criticalLatencyMs()) {
            score -= 40;
            issues.add(new Issue("LATENCY_CRITICAL", "CRITICAL", "Very high latency",
                    String.format("Round-trip time %.0f ms is above the critical limit of %d ms.", measured, config.health().criticalLatencyMs()) + pathNote,
                    "Likely causes: congested or overloaded link, weak Wi-Fi signal, busy device, or a slow WAN path. Test from a wired machine and check the switch port/AP load."));
        } else if (measured >= config.health().highLatencyMs()) {
            score -= 20;
            issues.add(new Issue("LATENCY_HIGH", "WARNING", "High latency",
                    String.format("Round-trip time %.0f ms is above the warning limit of %d ms.", measured, config.health().highLatencyMs()) + pathNote,
                    "Check Wi-Fi signal strength, uplink utilisation and whether a download/backup is saturating the link."));
        } else if (d.isGateway() && measured >= 100) {
            score -= 10;
            issues.add(new Issue("GATEWAY_SLOW", "WARNING", "Slow gateway response",
                    String.format("Gateway answers in %.0f ms. Anything above ~100 ms to the first hop is unusual (Wi-Fi and phone hotspots are often 20-80 ms).", measured),
                    "A slow first hop slows every client. Check the router CPU load and the Wi-Fi/Ethernet link to it."));
        }

        double loss = d.getPacketLossPercent();
        int samples = d.getLatencyHistory().size();
        if (samples >= 4 && loss >= config.health().lossCriticalPercent()) {
            score -= (int) Math.min(50, loss);
            issues.add(new Issue("LOSS_CRITICAL", "CRITICAL", "Severe packet loss",
                    String.format("%.0f%% of the last %d probes were lost.", loss, samples),
                    "Intermittent connectivity: check the cable/connector, Wi-Fi interference, duplex mismatch or a failing switch port."));
        } else if (samples >= 4 && loss >= config.health().lossWarningPercent()) {
            score -= (int) Math.min(30, loss);
            issues.add(new Issue("LOSS_WARNING", "WARNING", "Packet loss",
                    String.format("%.0f%% of the last %d probes were lost.", loss, samples),
                    "Some packets are dropped. Watch it over time; check radio interference or an overloaded link."));
        }

        Double jitter = d.getJitterMs();
        if (jitter != null && jitter >= config.health().jitterWarningMs()) {
            score -= 10;
            issues.add(new Issue("JITTER", "WARNING", "Unstable latency (jitter)",
                    String.format("Latency varies by about %.0f ms between probes.", jitter),
                    "Unstable links hurt voice/video and remote desktop. Look for Wi-Fi interference or bursty traffic."));
        }

        int flaps = d.stateChangesWithin(Duration.ofMinutes(15));
        if (flaps >= 3) {
            score -= 15;
            issues.add(new Issue("FLAPPING", "WARNING", "Device keeps going up and down",
                    flaps + " online/offline transitions in the last 15 minutes.",
                    "Check the cable/PoE, the Wi-Fi association (roaming) and the power supply."));
        }

        if (d.getHostname() == null && !d.isLocal())
            issues.add(new Issue("NO_NAME", "INFO", "No hostname found", "Reverse DNS and NetBIOS gave no name.",
                    "Add a DNS/DHCP record if this device should be identifiable."));

        score = Math.max(0, Math.min(100, score));
        boolean anyCritical = issues.stream().anyMatch(i -> "CRITICAL".equals(i.severity()));
        String status = anyCritical || score < 50 ? "CRITICAL" : score < 80 || issues.stream().anyMatch(i -> "WARNING".equals(i.severity())) ? "DEGRADED" : "HEALTHY";

        d.setIssues(issues);
        d.setHealthScore(score);
        d.setHealthStatus(status);
        d.setDiagnosis(buildDiagnosis(d, issues, status, who));
    }

    private String buildDiagnosis(Device d, List<Issue> issues, String status, String who) {
        List<Issue> serious = issues.stream().filter(i -> !"INFO".equals(i.severity())).toList();
        if (serious.isEmpty()) {
            String lat = d.getLatencyMs() == null ? "" : String.format("Replies in %.0f ms", d.getLatencyMs());
            String avg = d.getAvgLatencyMs() == null ? "" : String.format(" (average %.0f ms)", d.getAvgLatencyMs());
            return "Healthy. " + lat + avg + ", " + String.format("%.0f", d.getPacketLossPercent()) + "% packet loss over the last "
                    + Math.max(1, d.getLatencyHistory().size()) + " probe(s). No problems detected.";
        }
        StringBuilder sb = new StringBuilder("DEGRADED".equals(status) ? "Degraded. " : "Needs attention. ");
        for (Issue i : serious) sb.append(i.title()).append(": ").append(i.detail()).append(' ');
        sb.append(serious.get(0).recommendation());
        return sb.toString();
    }

    /** Recomputes avg latency, jitter and packet loss from the sliding window of recent probes. */
    private void computeStats(Device d) {
        List<Double> h = d.getLatencyHistory();
        if (h.isEmpty()) {
            d.setAvgLatencyMs(null);
            d.setJitterMs(null);
            d.setPacketLossPercent(0);
            return;
        }
        int lost = 0;
        double sum = 0;
        int ok = 0;
        Double prev = null;
        double jitterSum = 0;
        int jitterN = 0;
        for (Double v : h) {
            if (v == null) {
                lost++;
                continue;
            }
            sum += v;
            ok++;
            if (prev != null) {
                jitterSum += Math.abs(v - prev);
                jitterN++;
            }
            prev = v;
        }
        d.setPacketLossPercent(Math.round(lost * 1000.0 / h.size()) / 10.0);
        d.setAvgLatencyMs(ok == 0 ? null : Math.round(sum / ok * 10.0) / 10.0);
        d.setJitterMs(jitterN == 0 ? null : Math.round(jitterSum / jitterN * 10.0) / 10.0);
    }
}
