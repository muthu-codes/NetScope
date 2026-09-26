package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.model.Device;
import com.netscope.util.MacAddressUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vendor lookup + device classification.
 * IMPORTANT: everything here is a HEURISTIC and is labelled that way in the UI:
 * "MAC prefix suggests vendor X" is not the same as "this device is made by X".
 */
@Service
public class DeviceIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIntelligenceService.class);

    public record VendorInfo(String vendor, String note) {
    }

    private static final List<String> NETWORK_VENDORS = List.of("cisco", "tp-link", "ubiquiti", "mikrotik", "aruba",
            "juniper", "d-link", "netgear", "zyxel", "ruckus", "fortinet", "extreme", "brocade", "tenda", "linksys");
    private static final List<String> PRINTER_VENDORS = List.of("canon", "epson", "brother", "ricoh", "xerox", "kyocera",
            "lexmark", "konica");
    private static final List<String> MOBILE_NAMES = List.of("iphone", "ipad", "android", "galaxy", "redmi", "oneplus",
            "pixel", "oppo", "vivo", "realme", "poco", "huawei", "honor", "moto", "-phone", "phone-");
    private static final List<String> NETWORK_NAMES = List.of("switch", "router", "gateway", "wlc", "accesspoint", "access-point");
    private static final List<String> PRINTER_NAMES = List.of("printer", "print-", "mfp", "laserjet", "officejet", "deskjet");
    private static final List<String> COMPUTER_NAMES = List.of("desktop", "laptop", "pc-", "-pc", "lab", "workstation", "win-", "macbook", "imac");

    private final NetScopeConfig config;
    private final Map<String, String> oui = new HashMap<>();

    public DeviceIntelligenceService(NetScopeConfig config) {
        this.config = config;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getResourceAsStream("/oui.csv")) {
            if (in != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq < 1) continue;
                        String vendor = line.substring(0, eq).trim();
                        for (String p : line.substring(eq + 1).split(",")) {
                            String key = p.trim().toUpperCase(Locale.ROOT);
                            if (key.length() == 6) oui.put(key, vendor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not read built-in OUI list: {}", e.getMessage());
        }
        String external = config.oui().file();
        if (external != null && !external.isBlank()) loadExternal(Path.of(external.trim()));
        log.info("Vendor database loaded: {} prefixes", oui.size());
    }

    /** Reads the IEEE oui.csv (Registry,Assignment,Organization Name,...) or a simple PREFIX,Vendor file. */
    private void loadExternal(Path file) {
        try {
            int before = oui.size();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                List<String> f = splitCsv(line);
                if (f.size() < 2) continue;
                String prefix = f.get(0).equalsIgnoreCase("MA-L") && f.size() >= 3 ? f.get(1) : f.get(0);
                String name = f.get(0).equalsIgnoreCase("MA-L") && f.size() >= 3 ? f.get(2) : f.get(1);
                prefix = prefix.trim().replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
                if (prefix.length() == 6 && prefix.matches("[0-9A-F]{6}") && !name.isBlank()) oui.put(prefix, name.trim());
            }
            log.info("Loaded {} extra vendor prefixes from {}", oui.size() - before, file);
        } catch (Exception e) {
            log.warn("Could not read netscope.oui.file {}: {}", file, e.getMessage());
        }
    }

    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    public VendorInfo lookupVendor(String mac) {
        if (mac == null)
            return new VendorInfo(null, "MAC address is not visible from here (the device is behind a router, so it is not in the ARP table).");
        if (MacAddressUtils.isLocallyAdministered(mac))
            return new VendorInfo(null, "Randomized/private MAC address (common on phones and modern laptops) - the prefix does not identify a manufacturer.");
        String key = MacAddressUtils.ouiKey(mac);
        String vendor = key == null ? null : oui.get(key);
        if (vendor == null) return new VendorInfo(null, "MAC prefix " + key + " is not in the vendor list (load the full IEEE list for better coverage).");
        return new VendorInfo(vendor, "MAC prefix suggests " + vendor + " (a hint, not proof of the manufacturer).");
    }

    /** ICMP TTL starts at 64 / 128 / 255 depending on the OS family and drops by one per router hop. */
    public String osHint(Integer ttl) {
        if (ttl == null) return null;
        if (ttl <= 64) return "Linux / Android / iOS / macOS family (TTL " + ttl + ", heuristic)";
        if (ttl <= 128) return "Windows family (TTL " + ttl + ", heuristic)";
        return "Network equipment family (TTL " + ttl + ", heuristic)";
    }

    public static boolean ttlLooksWindows(Integer ttl) {
        return ttl != null && ttl > 64 && ttl <= 128;
    }

    /** Sets deviceType + classificationReason. Order matters: strongest evidence first. */
    public void classify(Device d) {
        String host = d.getHostname() == null ? "" : d.getHostname().toLowerCase(Locale.ROOT);
        String vendor = d.getVendor() == null ? "" : d.getVendor().toLowerCase(Locale.ROOT);
        Integer ttl = d.getTtl();
        boolean randomMac = d.getMac() != null && MacAddressUtils.isLocallyAdministered(d.getMac());

        if (d.isLocal()) {
            set(d, "LOCAL_HOST", "This is the computer running NetScope.");
        } else if (d.isGateway()) {
            set(d, "GATEWAY", "It is the next hop of this computer's default route (routing table).");
        } else if (containsAny(host, PRINTER_NAMES) || containsAny(vendor, PRINTER_VENDORS)) {
            set(d, "PRINTER", containsAny(host, PRINTER_NAMES) ? "Hostname looks like a printer." : "MAC prefix suggests a printer manufacturer (heuristic).");
        } else if (containsAny(host, NETWORK_NAMES) || containsAny(vendor, NETWORK_VENDORS)) {
            set(d, "NETWORK_DEVICE", containsAny(host, NETWORK_NAMES) ? "Hostname looks like network equipment." : "MAC prefix suggests a network-equipment vendor (heuristic).");
        } else if (ttl != null && ttl > 128) {
            set(d, "NETWORK_DEVICE", "ICMP TTL " + ttl + " is typical of routers/switches (heuristic).");
        } else if (containsAny(host, MOBILE_NAMES)) {
            set(d, "MOBILE", "Hostname looks like a phone/tablet.");
        } else if (randomMac && ttl != null && ttl <= 64) {
            set(d, "MOBILE", "Randomized MAC + TTL " + ttl + " is a pattern typical of phones (heuristic).");
        } else if (containsAny(host, COMPUTER_NAMES) || ttlLooksWindows(ttl)) {
            set(d, "COMPUTER", containsAny(host, COMPUTER_NAMES) ? "Hostname looks like a computer." : "TTL " + ttl + " suggests a Windows machine (heuristic).");
        } else if (vendor.contains("espressif") || vendor.contains("raspberry")) {
            set(d, "DEVICE", "IoT/embedded board vendor prefix (heuristic).");
        } else if (ttl != null) {
            set(d, "DEVICE", "Responds to ping; not enough evidence for a more specific type.");
        } else {
            set(d, "UNKNOWN", "Seen in the ARP table but it does not answer ping, so there is little evidence.");
        }
    }

    private static void set(Device d, String type, String reason) {
        d.setDeviceType(type);
        d.setClassificationReason(reason);
    }

    private static boolean containsAny(String text, List<String> needles) {
        if (text == null || text.isEmpty()) return false;
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }
}
