package com.netscope.controller;

import com.netscope.dto.DeviceResponse;
import com.netscope.dto.ObservationPoint;
import com.netscope.dto.SubnetSummary;
import com.netscope.service.DeviceDiscoveryService;
import com.netscope.service.PersistenceService;
import com.netscope.util.IpAddressUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceDiscoveryService discovery;
    private final PersistenceService persistence;

    public DeviceController(DeviceDiscoveryService discovery, PersistenceService persistence) {
        this.discovery = discovery;
        this.persistence = persistence;
    }

    /** Optional filters: q (text), state, type, health, subnet. */
    @GetMapping
    public List<DeviceResponse> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) String state,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) String health,
                                     @RequestParam(required = false) String subnet) {
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return discovery.devices().stream()
                .filter(d -> state == null || state.isBlank() || d.state().equalsIgnoreCase(state))
                .filter(d -> type == null || type.isBlank() || d.deviceType().equalsIgnoreCase(type))
                .filter(d -> health == null || health.isBlank() || d.healthStatus().equalsIgnoreCase(health))
                .filter(d -> subnet == null || subnet.isBlank() || subnet.equals(d.subnet()))
                .filter(d -> needle == null || needle.isEmpty() || matches(d, needle))
                .toList();
    }

    private static boolean matches(DeviceResponse d, String needle) {
        return contains(d.ip(), needle) || contains(d.mac(), needle) || contains(d.hostname(), needle) || contains(d.vendor(), needle);
    }

    private static boolean contains(String v, String needle) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(needle);
    }

    @GetMapping("/subnets")
    public List<SubnetSummary> subnets() {
        return discovery.subnets();
    }

    @GetMapping("/{ip:.+}")
    public DeviceResponse one(@PathVariable String ip) {
        requireIp(ip);
        return discovery.device(ip).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device " + ip + " has not been observed."));
    }

    @GetMapping("/{ip:.+}/history")
    public List<ObservationPoint> history(@PathVariable String ip) {
        requireIp(ip);
        return persistence.history(ip);
    }

    private static void requireIp(String ip) {
        if (!IpAddressUtils.isValidIpv4(ip)) throw new IllegalArgumentException("'" + ip + "' is not a valid IPv4 address.");
    }
}
