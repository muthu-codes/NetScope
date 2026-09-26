package com.netscope.zone;

import com.netscope.util.Cidr;
import com.netscope.util.IpAddressUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates and normalizes user input. Throws IllegalArgumentException with a message the UI can show directly. */
public final class ZoneValidator {

    public record Normalized(String name, String cidr, String description, ZoneType type, boolean enabled,
                             boolean authorized, String authorizedBy, String gateway, String methods, String tcpPorts,
                             boolean useNmap, int maxConcurrency, int timeoutMs, int maxDevices, int intervalSeconds,
                             int failureThreshold) {
    }

    private ZoneValidator() {
    }

    public static Normalized validate(ZoneRequest r) {
        if (r == null) throw new IllegalArgumentException("Request body is missing.");
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("Zone name is required (max 80 characters).");

        Cidr c = parseCidr(r.cidr());
        ZoneType type = parseType(r.zoneType());

        boolean authorized = type == ZoneType.LOCAL || Boolean.TRUE.equals(r.authorized());
        String by = r.authorizedBy() == null ? "" : r.authorizedBy().trim();
        if (by.length() > 120) throw new IllegalArgumentException("'Authorized by' is too long (max 120 characters).");
        if (type == ZoneType.LOCAL && by.isEmpty()) by = "local subnet";
        if (authorized && by.isEmpty())
            throw new IllegalArgumentException("Authorized zones need 'authorized by' (who approved monitoring this network). It is written to the audit log.");

        String gw = r.gateway() == null ? "" : r.gateway().trim();
        if (!gw.isEmpty() && (!IpAddressUtils.isValidIpv4(gw) || !IpAddressUtils.isPrivate(gw)))
            throw new IllegalArgumentException("Gateway '" + gw + "' must be a valid private IPv4 address.");

        String desc = r.description() == null ? "" : r.description().trim();
        if (desc.length() > 300) throw new IllegalArgumentException("Description is too long (max 300 characters).");

        return new Normalized(name, c.toString(), desc, type, r.enabled() == null || r.enabled(), authorized, by, gw,
                parseMethods(r.methods()), parsePorts(r.tcpPorts()), r.useNmap() == null || r.useNmap(),
                range("Max concurrency", r.maxConcurrency(), 1, 200, 20),
                range("Timeout (ms)", r.timeoutMs(), 200, 10000, 1000),
                range("Max devices", r.maxDevices(), 1, 4096, 512),
                range("Interval (s)", r.intervalSeconds(), 10, 86400, 60),
                range("Failure threshold", r.failureThreshold(), 1, 10, 3));
    }

    public static Cidr parseCidr(String text) {
        Cidr c;
        try {
            c = Cidr.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CIDR '" + (text == null ? "" : text) + "'. Use the form 10.20.30.0/24.");
        }
        if (text.trim().indexOf('/') < 0) throw new IllegalArgumentException("CIDR must include a prefix length, e.g. 10.20.30.0/24.");
        if (!c.isPrivate())
            throw new IllegalArgumentException("Refusing " + c + ": only private ranges (10/8, 172.16/12, 192.168/16, 100.64/10) can be monitored. NetScope never scans public networks.");
        if (c.prefix() < 16)
            throw new IllegalArgumentException("Zone " + c + " is too large. Use a /16 or smaller block per zone (split bigger networks into several zones).");
        return c;
    }

    static ZoneType parseType(String t) {
        if (t == null || t.isBlank()) return ZoneType.ROUTED;
        try {
            return ZoneType.valueOf(t.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown zone type '" + t + "'. Allowed: LOCAL, ROUTED, SERVER, MANAGEMENT, CUSTOM.");
        }
    }

    static String parseMethods(String m) {
        if (m == null || m.isBlank()) return "ICMP,TCP";
        Set<String> out = new LinkedHashSet<>();
        for (String s : m.split(",")) {
            String v = s.trim().toUpperCase(Locale.ROOT);
            if (v.isEmpty()) continue;
            if (!v.equals("ICMP") && !v.equals("TCP"))
                throw new IllegalArgumentException("Unknown monitoring method '" + v + "'. Allowed: ICMP, TCP.");
            out.add(v);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Choose at least one monitoring method (ICMP, TCP).");
        return String.join(",", out);
    }

    static String parsePorts(String p) {
        if (p == null || p.isBlank()) return "80,443,22";
        Set<Integer> out = new LinkedHashSet<>();
        for (String s : p.split(",")) {
            String v = s.trim();
            if (v.isEmpty()) continue;
            int n;
            try {
                n = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("TCP port '" + v + "' is not a number.");
            }
            if (n < 1 || n > 65535) throw new IllegalArgumentException("TCP port " + n + " is out of range (1-65535).");
            out.add(n);
        }
        if (out.isEmpty()) return "80,443,22";
        if (out.size() > 10) throw new IllegalArgumentException("Use at most 10 TCP ports per zone.");
        return out.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static List<Integer> portList(String csv) {
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    }

    private static int range(String label, Integer v, int min, int max, int def) {
        if (v == null) return def;
        if (v < min || v > max) throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        return v;
    }
}
