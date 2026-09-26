package com.netscope.service;

import com.netscope.model.NeighborEntry;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.MacAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the OS neighbour table (ARP cache). It only lists devices on the SAME Layer-2 segment as this computer,
 * which is why MAC addresses are unavailable for devices behind a router.
 */
@Service
public class NeighborTableService {

    private static final Pattern WIN_PS = Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\\|([^|]*)\\|(\\w*)\\|.*$");
    private static final Pattern WIN_ARP = Pattern.compile(
            "^\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9A-Fa-f]{2}(?:-[0-9A-Fa-f]{2}){5})\\s+(\\w+)");
    private static final Pattern LINUX = Pattern.compile("^(\\S+)\\s+dev\\s+\\S+\\s+lladdr\\s+(\\S+)\\s+(\\w+)");
    private static final Pattern MAC_ARP = Pattern.compile("\\((\\d+\\.\\d+\\.\\d+\\.\\d+)\\)\\s+at\\s+([0-9a-fA-F:]+)\\s");

    private final CommandExecutor exec;

    public NeighborTableService(CommandExecutor exec) {
        this.exec = exec;
    }

    /** Filtered, de-duplicated neighbour entries (no multicast / broadcast / loopback / link-local / empty MACs). */
    public List<NeighborEntry> read() {
        List<NeighborEntry> raw = switch (NetworkUtils.os()) {
            case WINDOWS -> readWindows();
            case MAC -> parseMacArp(exec.run(8000, "arp", "-an").output());
            default -> parseLinuxNeigh(exec.run(8000, "ip", "-4", "neigh", "show").output());
        };
        Map<String, NeighborEntry> byIp = new LinkedHashMap<>();
        for (NeighborEntry e : raw) {
            if (!isUsableEntry(e)) continue;
            NeighborEntry old = byIp.get(e.ip());
            if (old == null || (!isFresh(old.state()) && isFresh(e.state()))) byIp.put(e.ip(), e);
        }
        return new ArrayList<>(byIp.values());
    }

    public Map<String, NeighborEntry> asMap() {
        Map<String, NeighborEntry> m = new LinkedHashMap<>();
        for (NeighborEntry e : read()) m.put(e.ip(), e);
        return m;
    }

    public Optional<String> macFor(String ip) {
        return read().stream().filter(e -> e.ip().equals(ip)).map(NeighborEntry::mac).findFirst();
    }

    /** An entry the OS recently confirmed (device answered ARP just now) - proof the device is on the LAN. */
    public static boolean isFresh(String state) {
        if (state == null) return false;
        return switch (state.toUpperCase()) {
            case "REACHABLE", "DELAY", "PROBE", "DYNAMIC" -> true;
            default -> false;
        };
    }

    private static boolean isUsableEntry(NeighborEntry e) {
        return IpAddressUtils.isUsableUnicast(e.ip()) && MacAddressUtils.isUsable(e.mac());
    }

    private List<NeighborEntry> readWindows() {
        String script = "Get-NetNeighbor -AddressFamily IPv4 -ErrorAction SilentlyContinue | ForEach-Object { "
                + "'{0}|{1}|{2}|{3}' -f $_.IPAddress, $_.LinkLayerAddress, $_.State, $_.InterfaceIndex }";
        List<NeighborEntry> list = parseWindowsPowerShell(exec.powershell(15000, script).output());
        if (!list.isEmpty()) return list;
        return parseWindowsArp(exec.run(8000, "arp", "-a").output());     // fallback
    }

    // ---- parsers (public static so they can be unit-tested with sample text) ----

    public static List<NeighborEntry> parseWindowsPowerShell(String text) {
        List<NeighborEntry> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            Matcher m = WIN_PS.matcher(line.trim());
            if (!m.matches()) continue;
            String mac = MacAddressUtils.normalize(m.group(2));
            if (mac == null) continue;
            out.add(new NeighborEntry(m.group(1), mac, m.group(3).toUpperCase()));
        }
        return out;
    }

    public static List<NeighborEntry> parseWindowsArp(String text) {
        List<NeighborEntry> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            Matcher m = WIN_ARP.matcher(line);
            if (!m.find()) continue;
            String mac = MacAddressUtils.normalize(m.group(2));
            if (mac != null) out.add(new NeighborEntry(m.group(1), mac, m.group(3).toUpperCase()));
        }
        return out;
    }

    public static List<NeighborEntry> parseLinuxNeigh(String text) {
        List<NeighborEntry> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            Matcher m = LINUX.matcher(line.trim());
            if (!m.find()) continue;
            String mac = MacAddressUtils.normalize(m.group(2));
            if (mac != null) out.add(new NeighborEntry(m.group(1), mac, m.group(3).toUpperCase()));
        }
        return out;
    }

    public static List<NeighborEntry> parseMacArp(String text) {
        List<NeighborEntry> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\R")) {
            Matcher m = MAC_ARP.matcher(line);
            if (!m.find()) continue;
            String mac = MacAddressUtils.normalize(m.group(2));
            if (mac != null) out.add(new NeighborEntry(m.group(1), mac, "DYNAMIC"));
        }
        return out;
    }
}
