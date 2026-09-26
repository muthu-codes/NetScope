package com.netscope.util;

import java.util.regex.Pattern;

/** MAC address helpers. Canonical form used everywhere in NetScope: AA:BB:CC:DD:EE:FF */
public final class MacAddressUtils {

    private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]+");
    private static final Pattern HEX12 = Pattern.compile("^[0-9A-Fa-f]{12}$");

    private MacAddressUtils() {
    }

    /** Accepts 1A-03-0C-3A-4B-99, 1a:03:0c:3a:4b:99, 1a03.0c3a.4b99, 1a030c3a4b99 and macOS short forms (0:1:2:3:4:5). */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        String hex;
        String[] parts = s.split("[:\\-]");
        if (parts.length == 6) {
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty() || p.length() > 2 || !HEX.matcher(p).matches()) return null;
                sb.append(p.length() == 1 ? "0" + p : p);
            }
            hex = sb.toString();
        } else {
            hex = s.replaceAll("[:.\\-]", "");
            if (!HEX12.matcher(hex).matches()) return null;
        }
        hex = hex.toUpperCase();
        StringBuilder out = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) out.append(':');
            out.append(hex, i, i + 2);
        }
        return out.toString();
    }

    public static String fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 6) return null;
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    private static int firstOctet(String mac) {
        return Integer.parseInt(mac.substring(0, 2), 16);
    }

    /** A real unicast device MAC: not empty, not all zeros, not broadcast, not multicast. */
    public static boolean isUsable(String mac) {
        String m = normalize(mac);
        if (m == null) return false;
        if (m.equals("00:00:00:00:00:00") || m.equals("FF:FF:FF:FF:FF:FF")) return false;
        return (firstOctet(m) & 1) == 0;
    }

    /**
     * "Locally administered" MACs are randomly generated (phones do this per Wi-Fi network for privacy)
     * so their prefix does NOT identify a manufacturer.
     */
    public static boolean isLocallyAdministered(String mac) {
        String m = normalize(mac);
        return m != null && (firstOctet(m) & 2) != 0;
    }

    /** First 3 bytes without separators, e.g. "BCC746". */
    public static String ouiKey(String mac) {
        String m = normalize(mac);
        return m == null ? null : m.substring(0, 8).replace(":", "");
    }
}
