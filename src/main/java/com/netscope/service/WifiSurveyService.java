package com.netscope.service;

import com.netscope.dto.DeviceResponse;
import com.netscope.model.*;
import com.netscope.util.CommandExecutor;
import com.netscope.util.MacAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PASSIVE Wi-Fi survey: lists the access points this computer's adapter already hears (the same list as the Wi-Fi
 * menu in the taskbar). It never joins, probes, deauthenticates or otherwise touches those networks, and it cannot
 * see the devices connected to them - only the access points' own broadcasts.
 * Windows: netsh wlan show networks / interfaces.  Linux: nmcli dev wifi list.
 */
@Service
public class WifiSurveyService {

    private static final Pattern SSID_LINE = Pattern.compile("^\\s*SSID\\s+\\d+\\s*:\\s*(.*)$");
    private static final Pattern BSSID_LINE = Pattern.compile("^\\s*BSSID\\s+\\d+\\s*:\\s*(\\S+)");
    private static final Pattern KV = Pattern.compile("^\\s*([A-Za-z][A-Za-z ()/]*?)\\s*:\\s*(.*)$");

    public record NmcliParsed(List<WifiNetwork> networks, WifiConnection connection) {
    }

    private final CommandExecutor exec;
    private final DeviceIntelligenceService intelligence;
    private final DeviceDiscoveryService discovery;

    public WifiSurveyService(CommandExecutor exec, DeviceIntelligenceService intelligence, DeviceDiscoveryService discovery) {
        this.exec = exec;
        this.intelligence = intelligence;
        this.discovery = discovery;
    }

    public WifiSurvey survey() {
        Instant now = Instant.now();
        switch (NetworkUtils.os()) {
            case WINDOWS:
                return windows(now);
            case LINUX:
                return linux(now);
            default:
                return new WifiSurvey(now, false, "The Wi-Fi survey is supported on Windows and Linux.", null, List.of(), List.of(), List.of());
        }
    }

    // ------------------------------------------------------------------ Windows

    private WifiSurvey windows(Instant now) {
        String nets = exec.run(12_000, "netsh", "wlan", "show", "networks", "mode=bssid").output();
        String ifs = exec.run(8_000, "netsh", "wlan", "show", "interfaces").output();
        return assemble(now, parseWindowsNetworks(nets), parseWindowsInterface(ifs), windowsProblem(nets));
    }

    static String windowsProblem(String text) {
        if (text == null || text.isBlank()) return "Could not run netsh. Is this Windows with a Wi-Fi adapter?";
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("no wireless interface") || t.contains("wlansvc") || t.contains("autoconfig service"))
            return "No Wi-Fi adapter found, or the WLAN AutoConfig service is off (are you on Ethernet?).";
        if (t.contains("location") || t.contains("elevation") || t.contains("access is denied"))
            return "Windows is hiding Wi-Fi scan results. Turn on Settings > Privacy & security > Location "
                    + "(including \"Let desktop apps access your location\"), then refresh.";
        return null;
    }

    /** Parses `netsh wlan show networks mode=bssid` (English output). */
    public static List<WifiNetwork> parseWindowsNetworks(String text) {
        List<WifiNetwork> out = new ArrayList<>();
        if (text == null) return out;
        Acc a = new Acc();
        for (String raw : text.split("\\R")) {
            Matcher m = SSID_LINE.matcher(raw);
            if (m.matches()) {
                a.flush(out);
                a.ssid = m.group(1).trim();
                a.auth = null;
                a.enc = null;
                continue;
            }
            m = BSSID_LINE.matcher(raw);
            if (m.find()) {
                a.flush(out);
                a.bssid = MacAddressUtils.normalize(m.group(1));
                if (a.bssid == null) a.bssid = m.group(1);
                continue;
            }
            m = KV.matcher(raw);
            if (!m.matches()) continue;
            String v = m.group(2).trim();
            switch (m.group(1).trim().toLowerCase(Locale.ROOT)) {
                case "authentication" -> a.auth = v;
                case "encryption" -> a.enc = v;
                case "signal" -> a.signal = parseInt(v.replace("%", ""));
                case "radio type" -> a.radio = v;
                case "band" -> a.band = v;
                case "channel" -> a.channel = parseInt(v);
                default -> {
                }
            }
        }
        a.flush(out);
        return out;
    }

    private static final class Acc {
        String ssid, auth, enc, bssid, radio, band;
        Integer signal, channel;

        void flush(List<WifiNetwork> out) {
            if (bssid != null) {
                String level = securityLevel(auth, enc);
                out.add(new WifiNetwork(ssid == null ? "" : ssid, bssid, signal, dbmFor(signal), channel, bandFor(band, channel),
                        radio, auth, enc, level, isInsecure(level), null, false, false));
            }
            bssid = null;
            radio = null;
            band = null;
            signal = null;
            channel = null;
        }
    }

    /** Parses `netsh wlan show interfaces` and returns the first connected adapter (or null). */
    public static WifiConnection parseWindowsInterface(String text) {
        if (text == null) return null;
        List<Map<String, String>> blocks = new ArrayList<>();
        Map<String, String> cur = null;
        for (String raw : text.split("\\R")) {
            Matcher m = KV.matcher(raw);
            if (!m.matches()) continue;
            String k = m.group(1).trim().toLowerCase(Locale.ROOT);
            if (k.equals("name")) {
                cur = new HashMap<>();
                blocks.add(cur);
            }
            if (cur != null) cur.put(k, m.group(2).trim());
        }
        for (Map<String, String> b : blocks) {
            if (!"connected".equalsIgnoreCase(b.getOrDefault("state", ""))) continue;
            Integer sig = parseInt(b.getOrDefault("signal", "").replace("%", ""));
            Integer ch = parseInt(b.getOrDefault("channel", ""));
            String bssid = MacAddressUtils.normalize(b.get("bssid"));
            return new WifiConnection(b.get("ssid"), bssid, sig, dbmFor(sig), ch, bandFor(b.get("band"), ch), b.get("radio type"),
                    b.get("authentication"), parseDouble(b.get("receive rate (mbps)")), parseDouble(b.get("transmit rate (mbps)")),
                    qualityLabel(sig));
        }
        return null;
    }

    // ------------------------------------------------------------------ Linux

    private WifiSurvey linux(Instant now) {
        CommandExecutor.CommandResult r = exec.run(15_000, "nmcli", "-t", "-f", "IN-USE,SSID,BSSID,CHAN,FREQ,SIGNAL,SECURITY", "dev", "wifi", "list");
        if (r.startFailed())
            return new WifiSurvey(now, false, "nmcli (NetworkManager) was not found, so Wi-Fi networks cannot be listed.", null, List.of(), List.of(), List.of());
        NmcliParsed p = parseNmcli(r.output());
        return assemble(now, p.networks(), p.connection(), null);
    }

    /** Parses `nmcli -t -f IN-USE,SSID,BSSID,CHAN,FREQ,SIGNAL,SECURITY dev wifi list`. */
    public static NmcliParsed parseNmcli(String text) {
        List<WifiNetwork> out = new ArrayList<>();
        WifiConnection conn = null;
        if (text == null) return new NmcliParsed(out, null);
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String[] f = line.split("(?<!\\\\):", -1);
            if (f.length < 7) continue;
            for (int i = 0; i < f.length; i++) f[i] = f[i].replace("\\:", ":").trim();
            String bssid = MacAddressUtils.normalize(f[2]);
            if (bssid == null) continue;
            Integer ch = parseInt(f[3]);
            Integer freq = parseInt(f[4].replaceAll("[^0-9]", ""));
            Integer sig = parseInt(f[5]);
            String sec = f[6].equals("--") || f[6].isEmpty() ? "Open" : f[6];
            String level = securityLevel(sec, sec.equals("Open") ? "None" : "");
            String band = freq == null ? bandFor(null, ch) : freq < 3000 ? "2.4 GHz" : freq < 5900 ? "5 GHz" : "6 GHz";
            out.add(new WifiNetwork(f[1], bssid, sig, dbmFor(sig), ch, band, null, sec, null, level, isInsecure(level), null, false, false));
            if (f[0].equals("*")) conn = new WifiConnection(f[1], bssid, sig, dbmFor(sig), ch, band, null, sec, null, null, qualityLabel(sig));
        }
        return new NmcliParsed(out, conn);
    }

    // ------------------------------------------------------------------ analysis

    private WifiSurvey assemble(Instant now, List<WifiNetwork> parsed, WifiConnection conn, String problem) {
        List<WifiNetwork> nets = new ArrayList<>();
        for (WifiNetwork n : parsed) {
            boolean connected = conn != null && (n.bssid().equalsIgnoreCase(conn.bssid() == null ? "" : conn.bssid())
                    || (conn.bssid() == null && n.ssid().equals(conn.ssid())));
            nets.add(n.enriched(intelligence.lookupVendor(n.bssid()).vendor(), MacAddressUtils.isLocallyAdministered(n.bssid()), connected));
        }
        nets.sort(Comparator.comparingInt((WifiNetwork n) -> n.signalPercent() == null ? -1 : n.signalPercent()).reversed());

        boolean available = !nets.isEmpty() || conn != null;
        String message = problem != null ? problem : nets.isEmpty() ? "No Wi-Fi networks were reported (Wi-Fi may be off or still scanning)." : null;
        if (nets.isEmpty() && conn != null && problem != null) message = problem;

        List<WifiChannelUsage> channels = channelUsage(nets, conn);
        return new WifiSurvey(now, available, message, conn, nets, channels, insights(nets, conn, channels));
    }

    /** Per-channel usage. All 2.4 GHz channels 1-13 are listed (empty ones show free spectrum); 5/6 GHz only used channels. */
    static List<WifiChannelUsage> channelUsage(List<WifiNetwork> nets, WifiConnection conn) {
        List<WifiChannelUsage> out = new ArrayList<>();
        for (int ch = 1; ch <= 13; ch++) {
            int count = 0;
            Integer strongest = null;
            for (WifiNetwork n : nets) {
                if (!"2.4 GHz".equals(n.band()) || n.channel() == null || n.channel() != ch) continue;
                count++;
                if (n.signalPercent() != null && (strongest == null || n.signalPercent() > strongest)) strongest = n.signalPercent();
            }
            boolean here = conn != null && "2.4 GHz".equals(conn.band()) && conn.channel() != null && conn.channel() == ch;
            out.add(new WifiChannelUsage("2.4 GHz", ch, count, strongest, round2(overlapLoad(nets, ch)), here));
        }
        Map<String, WifiChannelUsage> higher = new TreeMap<>();
        for (WifiNetwork n : nets) {
            if ("2.4 GHz".equals(n.band()) || n.channel() == null) continue;
            String key = n.band() + "#" + String.format("%03d", n.channel());
            WifiChannelUsage old = higher.get(key);
            int count = old == null ? 1 : old.apCount() + 1;
            Integer s = n.signalPercent();
            Integer strongest = old == null ? s : (s != null && (old.strongestSignal() == null || s > old.strongestSignal()) ? s : old.strongestSignal());
            boolean here = conn != null && n.band() != null && n.band().equals(conn.band()) && n.channel().equals(conn.channel());
            higher.put(key, new WifiChannelUsage(n.band(), n.channel(), count, strongest, count, here || (old != null && old.connectedHere())));
        }
        out.addAll(higher.values());
        return out;
    }

    /** 2.4 GHz channels are 5 MHz apart but 20 MHz wide, so networks up to 4 channels away interfere. */
    static double overlapLoad(List<WifiNetwork> nets, int channel) {
        double load = 0;
        for (WifiNetwork n : nets) {
            if (!"2.4 GHz".equals(n.band()) || n.channel() == null) continue;
            double overlap = Math.max(0, 1 - Math.abs(channel - n.channel()) / 5.0);
            load += overlap * ((n.signalPercent() == null ? 50 : n.signalPercent()) / 100.0);
        }
        return load;
    }

    private List<String> insights(List<WifiNetwork> nets, WifiConnection conn, List<WifiChannelUsage> channels) {
        List<String> out = new ArrayList<>();

        if (conn != null && conn.signalPercent() != null) {
            out.add(String.format("Connected to \"%s\" with %d%% signal (%s), channel %s%s.", conn.ssid(), conn.signalPercent(),
                    conn.qualityLabel().toLowerCase(Locale.ROOT), conn.channel() == null ? "?" : conn.channel(),
                    conn.band() == null ? "" : ", " + conn.band()));
            Optional<DeviceResponse> gw = discovery.gatewayDevice();
            if (gw.isPresent() && "REACHABLE".equals(gw.get().state()) && gw.get().latencyMs() != null) {
                double ms = gw.get().latencyMs();
                if (conn.signalPercent() < 50 && ms >= 30)
                    out.add(String.format("The signal is weak and the gateway answers in %.0f ms: poor radio quality is a likely cause of the slow first hop.", ms));
                else if (conn.signalPercent() >= 70 && ms >= 80)
                    out.add(String.format("The signal is strong but the gateway still takes %.0f ms. The delay is probably the router/phone itself (hotspots often add 20-80 ms), not radio range.", ms));
            }
        }

        // best of the three non-overlapping 2.4 GHz channels
        Map<Integer, Double> load = new LinkedHashMap<>();
        for (int ch : new int[]{1, 6, 11}) load.put(ch, overlapLoad(nets, ch));
        boolean any24 = nets.stream().anyMatch(n -> "2.4 GHz".equals(n.band()));
        if (any24) {
            int best = Collections.min(load.entrySet(), Map.Entry.comparingByValue()).getKey();
            if (conn != null && "2.4 GHz".equals(conn.band()) && conn.channel() != null && conn.channel() <= 13) {
                double mine = overlapLoad(nets, conn.channel());
                if (mine - load.get(best) >= 0.8)
                    out.add(String.format("2.4 GHz channel %d is crowded (load %.1f); channel %d is quieter (%.1f). If you manage this access point, moving it would reduce interference.",
                            conn.channel(), mine, best, load.get(best)));
            } else {
                out.add(String.format("Quietest 2.4 GHz channel right now: %d (load %.1f).", best, load.get(best)));
            }
        }

        long open = nets.stream().filter(n -> "OPEN".equals(n.securityLevel())).map(WifiNetwork::ssid).distinct().count();
        if (open > 0) out.add(open + " open network(s) in range: traffic on them is not encrypted, so avoid sensitive logins there.");
        long weak = nets.stream().filter(n -> "WEP".equals(n.securityLevel()) || "WPA".equals(n.securityLevel())).count();
        if (weak > 0) out.add(weak + " access point(s) use outdated security (WEP or WPA v1), which can be broken quickly.");

        Map<String, Long> bySsid = new HashMap<>();
        for (WifiNetwork n : nets) if (!n.ssid().isBlank()) bySsid.merge(n.ssid(), 1L, Long::sum);
        bySsid.entrySet().stream().max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= 3)
                .ifPresent(e -> out.add("\"" + e.getKey() + "\" is heard on " + e.getValue() + " access points: a managed multi-AP network where devices can roam."));

        long hidden = nets.stream().filter(n -> n.ssid().isBlank()).count();
        if (hidden > 0) out.add(hidden + " access point(s) hide their network name.");
        long priv = nets.stream().filter(WifiNetwork::privateBssid).count();
        if (priv > 0) out.add(priv + " access point(s) use a private (randomized) BSSID, which is typical of phone hotspots and virtual SSIDs.");
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static String securityLevel(String auth, String enc) {
        String a = auth == null ? "" : auth.toLowerCase(Locale.ROOT);
        String e = enc == null ? "" : enc.toLowerCase(Locale.ROOT);
        if (a.contains("enhanced") || a.contains("owe")) return "OWE";
        if (a.contains("wpa3") || a.contains("sae")) return "WPA3";
        if (a.contains("wpa2")) return "WPA2";
        if (a.contains("wpa")) return "WPA";
        if (e.contains("wep") || a.contains("wep") || a.contains("shared")) return "WEP";
        if (a.contains("open") || a.equals("none") || (a.isEmpty() && e.equals("none"))) return "OPEN";
        return "UNKNOWN";
    }

    static boolean isInsecure(String level) {
        return level.equals("OPEN") || level.equals("WEP") || level.equals("WPA");
    }

    static String bandFor(String band, Integer channel) {
        if (band != null && !band.isBlank()) {
            String b = band.toLowerCase(Locale.ROOT);
            if (b.contains("2.4")) return "2.4 GHz";
            if (b.contains("6")) return "6 GHz";
            if (b.contains("5")) return "5 GHz";
        }
        if (channel == null) return null;
        return channel <= 14 ? "2.4 GHz" : "5 GHz";
    }

    /** Windows reports signal quality 0-100; it maps to roughly dBm = quality / 2 - 100. */
    static Integer dbmFor(Integer signalPercent) {
        return signalPercent == null ? null : signalPercent / 2 - 100;
    }

    static String qualityLabel(Integer signal) {
        if (signal == null) return "Unknown";
        if (signal >= 80) return "Excellent";
        if (signal >= 60) return "Good";
        if (signal >= 40) return "Fair";
        return "Weak";
    }

    private static Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return s == null ? null : Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
