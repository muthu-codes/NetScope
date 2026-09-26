package com.netscope.service;

import com.netscope.model.GatewayInfo;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the default IPv4 gateway from the ROUTING TABLE (not by parsing ipconfig text) and then asks the
 * neighbour table for the gateway's MAC address.
 * Windows: Get-NetRoute 0.0.0.0/0, lowest (route metric + interface metric) wins.
 */
@Service
public class GatewayService {

    private static final Pattern LINUX_DEFAULT = Pattern.compile("default\\s+via\\s+(\\S+)\\s+dev\\s+(\\S+)(?:.*?metric\\s+(\\d+))?");

    private final CommandExecutor exec;
    private final NeighborTableService neighbors;
    private final PingService ping;

    private Optional<GatewayInfo> cached = Optional.empty();
    private long cachedAt = 0;

    public GatewayService(CommandExecutor exec, NeighborTableService neighbors, PingService ping) {
        this.exec = exec;
        this.neighbors = neighbors;
        this.ping = ping;
    }

    public synchronized Optional<GatewayInfo> defaultGateway() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 10_000) return cached;

        List<GatewayInfo> routes = switch (NetworkUtils.os()) {
            case WINDOWS -> windowsRoutes();
            case MAC -> macRoutes();
            default -> linuxRoutes();
        };
        Optional<GatewayInfo> best = routes.stream()
                .min(Comparator.comparingInt(g -> g.metric() == null ? Integer.MAX_VALUE : g.metric()));

        if (best.isPresent()) {
            GatewayInfo g = best.get();
            Optional<String> mac = neighbors.macFor(g.ip());
            if (mac.isEmpty()) {                       // make sure the OS has an ARP entry, then read again
                ping.ping(g.ip(), 1000);
                mac = neighbors.macFor(g.ip());
            }
            best = Optional.of(mac.map(g::withMac).orElse(g));
        }
        cached = best;
        cachedAt = System.currentTimeMillis();
        return cached;
    }

    private List<GatewayInfo> windowsRoutes() {
        String script = "Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | "
                + "ForEach-Object { $m = (Get-NetIPInterface -InterfaceIndex $_.InterfaceIndex -AddressFamily IPv4 "
                + "-ErrorAction SilentlyContinue).InterfaceMetric; "
                + "'{0}|{1}|{2}' -f $_.NextHop, $_.InterfaceIndex, ($_.RouteMetric + $m) }";
        return parseWindowsRoutes(exec.powershell(15000, script).output());
    }

    public static List<GatewayInfo> parseWindowsRoutes(String text) {
        List<GatewayInfo> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            String[] p = line.trim().split("\\|");
            if (p.length < 3) continue;
            String hop = p[0].trim();
            if (!IpAddressUtils.isUsableUnicast(hop)) continue;      // skips 0.0.0.0 (on-link routes)
            out.add(new GatewayInfo(hop, null, parseInt(p[1]), parseInt(p[2]), "routing-table (Get-NetRoute)"));
        }
        return out;
    }

    private List<GatewayInfo> linuxRoutes() {
        return parseLinuxRoutes(exec.run(8000, "ip", "-4", "route", "show", "default").output());
    }

    public static List<GatewayInfo> parseLinuxRoutes(String text) {
        List<GatewayInfo> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            Matcher m = LINUX_DEFAULT.matcher(line.trim());
            if (m.find() && IpAddressUtils.isUsableUnicast(m.group(1))) {
                out.add(new GatewayInfo(m.group(1), null, null, m.group(3) == null ? 0 : Integer.parseInt(m.group(3)),
                        "routing-table (ip route)"));
            }
        }
        return out;
    }

    private List<GatewayInfo> macRoutes() {
        List<GatewayInfo> out = new ArrayList<>();
        String text = exec.run(8000, "route", "-n", "get", "default").output();
        Matcher m = Pattern.compile("gateway:\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(text);
        if (m.find()) out.add(new GatewayInfo(m.group(1), null, null, 0, "routing-table (route get)"));
        return out;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
