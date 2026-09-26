package com.netscope.zone;

import com.netscope.util.Cidr;
import com.netscope.util.IpAddressUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the host routing table (Windows "route print -4" or Linux "ip -4 route"). */
public final class RouteParser {

    /** type: DEFAULT, DIRECT (on-link) or GATEWAY (via next hop). nextHop is null for DIRECT routes. */
    public record RouteEntry(String destination, String nextHop, String iface, String type, Integer metric) {
    }

    private static final Pattern WIN = Pattern.compile(
            "^\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(On-link|\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+)\\s*$");
    private static final Pattern LIN_VIA = Pattern.compile("\\bvia (\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static final Pattern LIN_DEV = Pattern.compile("\\bdev (\\S+)");
    private static final Pattern LIN_METRIC = Pattern.compile("\\bmetric (\\d+)");

    private RouteParser() {
    }

    public static List<RouteEntry> parseWindows(String output) {
        List<RouteEntry> out = new ArrayList<>();
        if (output == null) return out;
        for (String line : output.split("\\R")) {
            Matcher m = WIN.matcher(line);
            if (!m.matches()) continue;
            String dest = m.group(1), mask = m.group(2), gw = m.group(3), iface = m.group(4);
            if (!IpAddressUtils.isValidIpv4(dest) || !IpAddressUtils.isValidIpv4(mask)) continue;
            long d = IpAddressUtils.toLong(dest);
            if (IpAddressUtils.isLoopback(d) || IpAddressUtils.isMulticastOrReserved(d)) continue;
            int prefix = Long.bitCount(IpAddressUtils.toLong(mask));
            if (prefix == 32 && gw.equalsIgnoreCase("On-link")) continue;      // host routes to own addresses
            String cidr = Cidr.of(d, prefix).toString();
            boolean isDefault = prefix == 0;
            out.add(new RouteEntry(cidr, gw.equalsIgnoreCase("On-link") ? null : gw, iface,
                    isDefault ? "DEFAULT" : gw.equalsIgnoreCase("On-link") ? "DIRECT" : "GATEWAY", Integer.parseInt(m.group(5))));
        }
        return out;
    }

    public static List<RouteEntry> parseLinux(String output) {
        List<RouteEntry> out = new ArrayList<>();
        if (output == null) return out;
        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String first = line.split("\\s+")[0];
            String cidr;
            boolean isDefault = first.equals("default");
            if (isDefault) cidr = "0.0.0.0/0";
            else {
                try {
                    cidr = Cidr.parse(first).toString();
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
            Matcher via = LIN_VIA.matcher(line), dev = LIN_DEV.matcher(line), met = LIN_METRIC.matcher(line);
            String hop = via.find() ? via.group(1) : null;
            out.add(new RouteEntry(cidr, hop, dev.find() ? dev.group(1) : null,
                    isDefault ? "DEFAULT" : hop == null ? "DIRECT" : "GATEWAY", met.find() ? Integer.parseInt(met.group(1)) : null));
        }
        return out;
    }
}
