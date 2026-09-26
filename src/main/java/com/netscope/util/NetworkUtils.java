package com.netscope.util;

import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Small OS / interface / threading helpers. */
public final class NetworkUtils {

    public enum Os { WINDOWS, LINUX, MAC, OTHER }

    private static final Os OS = detect();

    private NetworkUtils() {
    }

    private static Os detect() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return Os.WINDOWS;
        if (n.contains("mac") || n.contains("darwin")) return Os.MAC;
        if (n.contains("nux") || n.contains("nix")) return Os.LINUX;
        return Os.OTHER;
    }

    public static Os os() {
        return OS;
    }

    public static boolean isWindows() {
        return OS == Os.WINDOWS;
    }

    public static boolean isMac() {
        return OS == Os.MAC;
    }

    public static String osName() {
        return System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", "");
    }

    /** Interfaces created by VMs, containers, VPN adapters and similar. They are not the "real" LAN connection. */
    public static boolean looksVirtual(String name, String display) {
        String s = ((name == null ? "" : name) + " " + (display == null ? "" : display)).toLowerCase(Locale.ROOT);
        String[] hints = {"vmware", "virtualbox", "vbox", "hyper-v", "vethernet", "docker", "veth", "wsl", "br-",
                "virbr", "tailscale", "zerotier", "vpn", "tap-", "tun", "loopback", "pseudo", "npcap", "bluetooth", "teredo", "isatap"};
        for (String h : hints) if (s.contains(h)) return true;
        return false;
    }

    /** WIFI / ETHERNET / VIRTUAL / LOOPBACK / OTHER (best-effort from the interface name). */
    public static String classifyInterface(String name, String display, boolean loopback) {
        if (loopback) return "LOOPBACK";
        String s = ((name == null ? "" : name) + " " + (display == null ? "" : display)).toLowerCase(Locale.ROOT);
        if (looksVirtual(name, display)) return "VIRTUAL";
        if (s.contains("wi-fi") || s.contains("wifi") || s.contains("wireless") || s.contains("wlan")
                || s.contains("802.11") || s.matches(".*\\bwl[a-z0-9]+.*")) return "WIFI";
        if (s.contains("ethernet") || s.contains("gigabit") || s.contains("realtek pcie") || s.matches(".*\\b(eth|en)[a-z0-9]+.*"))
            return "ETHERNET";
        return "OTHER";
    }

    public static void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** "3 min ago", "2 h ago" ... for human readable diagnosis text. */
    public static String ago(java.time.Instant then) {
        if (then == null) return "never";
        long s = Math.max(0, java.time.Duration.between(then, java.time.Instant.now()).getSeconds());
        if (s < 60) return s + " s ago";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }
}
