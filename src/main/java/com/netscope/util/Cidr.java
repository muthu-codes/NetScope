package com.netscope.util;

import java.util.ArrayList;
import java.util.List;

/** An IPv4 network block such as 10.114.0.0/16. */
public record Cidr(long network, int prefix) {

    public static Cidr parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty CIDR");
        String[] parts = text.trim().split("/");
        if (parts.length > 2) throw new IllegalArgumentException("Invalid CIDR: " + text);
        long ip = IpAddressUtils.toLong(parts[0]);
        int prefix;
        try {
            prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : 32;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR prefix: " + text);
        }
        if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("CIDR prefix must be 0-32: " + text);
        return of(ip, prefix);
    }

    public static Cidr of(long ip, int prefix) {
        long mask = maskFor(prefix);
        return new Cidr(ip & mask, prefix);
    }

    private static long maskFor(int prefix) {
        return prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }

    public long mask() {
        return maskFor(prefix);
    }

    public long broadcast() {
        return network | (~mask() & 0xFFFFFFFFL);
    }

    public long size() {
        return 1L << (32 - prefix);
    }

    /** Number of usable host addresses (excludes network + broadcast for prefixes below /31). */
    public long hostCount() {
        return prefix >= 31 ? size() : Math.max(0, size() - 2);
    }

    public boolean contains(long ip) {
        return (ip & mask()) == network;
    }

    public boolean contains(String ip) {
        return IpAddressUtils.isValidIpv4(ip) && contains(IpAddressUtils.toLong(ip));
    }

    public boolean contains(Cidr other) {
        return contains(other.network) && contains(other.broadcast());
    }

    public boolean isPrivate() {
        return IpAddressUtils.isPrivate(network) && IpAddressUtils.isPrivate(broadcast());
    }

    /** Every host address to probe. Skips network/broadcast and any x.x.x.0 / x.x.x.255 address. */
    public List<String> hosts() {
        List<String> out = new ArrayList<>((int) Math.min(hostCount(), 70000));
        long start = prefix >= 31 ? network : network + 1;
        long end = prefix >= 31 ? broadcast() : broadcast() - 1;
        for (long v = start; v <= end; v++) {
            long last = v & 255;
            if (prefix < 31 && (last == 0 || last == 255)) continue;
            out.add(IpAddressUtils.fromLong(v));
        }
        return out;
    }

    @Override
    public String toString() {
        return IpAddressUtils.fromLong(network) + "/" + prefix;
    }
}
