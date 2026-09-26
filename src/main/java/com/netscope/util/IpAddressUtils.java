package com.netscope.util;

import java.util.regex.Pattern;

/** IPv4 helper functions. IPv4 addresses are handled as unsigned 32-bit numbers stored in a long. */
public final class IpAddressUtils {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private IpAddressUtils() {
    }

    public static boolean isValidIpv4(String ip) {
        return ip != null && IPV4.matcher(ip).matches();
    }

    public static long toLong(String ip) {
        if (!isValidIpv4(ip)) throw new IllegalArgumentException("Not a valid IPv4 address: " + ip);
        long v = 0;
        for (String part : ip.split("\\.")) v = (v << 8) | Integer.parseInt(part);
        return v;
    }

    public static String fromLong(long v) {
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    /** RFC1918 private ranges + 100.64.0.0/10 (carrier-grade NAT, also used by some campuses/hotspots). */
    public static boolean isPrivate(long ip) {
        return (ip >> 24) == 10
                || (ip >> 20) == 0xAC1          // 172.16.0.0/12
                || (ip >> 16) == 0xC0A8         // 192.168.0.0/16
                || (ip >> 22) == 0x191;         // 100.64.0.0/10
    }

    public static boolean isPrivate(String ip) {
        return isValidIpv4(ip) && isPrivate(toLong(ip));
    }

    public static boolean isLoopback(long ip) {
        return (ip >> 24) == 127;
    }

    public static boolean isLinkLocal(long ip) {
        return (ip >> 16) == 0xA9FE;            // 169.254.0.0/16
    }

    public static boolean isMulticastOrReserved(long ip) {
        return (ip >> 28) >= 0xE;               // 224.0.0.0 and above (multicast, reserved, broadcast)
    }

    /** True for an address that can be a normal host on a LAN (not loopback/multicast/broadcast/link-local/0.0.0.0). */
    public static boolean isUsableUnicast(String ip) {
        if (!isValidIpv4(ip)) return false;
        long v = toLong(ip);
        return v != 0 && !isLoopback(v) && !isLinkLocal(v) && !isMulticastOrReserved(v);
    }

    /** "10.114.32.73" -> "10.114.32.0/24" (used to group devices into subnets on the topology). */
    public static String subnet24(String ip) {
        String[] p = ip.split("\\.");
        return p[0] + "." + p[1] + "." + p[2] + ".0/24";
    }

    public static String prefixToNetmask(int prefix) {
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return fromLong(mask);
    }
}
