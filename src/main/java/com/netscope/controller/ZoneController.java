package com.netscope.controller;

import com.netscope.dto.zone.*;
import com.netscope.zone.ZoneRequest;
import com.netscope.service.zone.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Multi-subnet campus network zones. Local-subnet discovery keeps working through the existing endpoints under
 * /api/network and /api/topology; this controller adds ROUTED/SERVER/MANAGEMENT/CUSTOM zone management on top.
 */
@RestController
@RequestMapping("/api/network/zones")
public class ZoneController {

    private final ZoneService service;

    public ZoneController(ZoneService service) {
        this.service = service;
    }

    @GetMapping
    public List<ZoneResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> create(@RequestBody ZoneRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ZoneResponse update(@PathVariable long id, @RequestBody ZoneRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ZoneResponse enable(@PathVariable long id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    @PostMapping("/{id}/scan")
    public ResponseEntity<Map<String, Object>> scan(@PathVariable long id) {
        service.startScan(id);
        return ResponseEntity.accepted().body(Map.of("started", true));
    }

    @GetMapping("/{id}/scan-status")
    public ZoneScanStatusResponse scanStatus(@PathVariable long id) {
        return service.scanStatus(id);
    }

    @PostMapping("/{id}/nmap-scan")
    public NmapScanResponse nmapScan(@PathVariable long id) {
        return service.nmapScan(id);
    }

    @GetMapping("/{id}/devices")
    public List<ZoneDeviceResponse> devices(@PathVariable long id) {
        return service.devices(id);
    }

    @GetMapping("/overview")
    public ZoneOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/incidents")
    public List<IncidentResponse> incidents() {
        return service.incidents();
    }

    @GetMapping("/topology")
    public ZoneTopologyResponse topology() {
        return service.topology();
    }

    @GetMapping("/routes")
    public List<RouteEntryResponse> routes() {
        return service.routeTable();
    }
}
