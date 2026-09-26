package com.netscope.controller;

import com.netscope.dto.DeviceStats;
import com.netscope.dto.NetworkSummaryResponse;
import com.netscope.model.MonitoringStatus;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.NetworkInterfaceInfo;
import com.netscope.model.ScopeInfo;
import com.netscope.service.DeviceDiscoveryService;
import com.netscope.service.MonitoringService;
import com.netscope.service.NetworkInfoService;
import com.netscope.service.ScopeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkInfoService networkInfo;
    private final DeviceDiscoveryService discovery;
    private final MonitoringService monitoring;
    private final ScopeService scope;

    public NetworkController(NetworkInfoService networkInfo, DeviceDiscoveryService discovery,
                             MonitoringService monitoring, ScopeService scope) {
        this.networkInfo = networkInfo;
        this.discovery = discovery;
        this.monitoring = monitoring;
        this.scope = scope;
    }

    @GetMapping("/configuration")
    public NetworkConfiguration configuration() {
        return networkInfo.configuration();
    }

    @GetMapping("/interfaces")
    public List<NetworkInterfaceInfo> interfaces() {
        return networkInfo.listInterfaces();
    }

    @GetMapping("/active")
    public ResponseEntity<NetworkInterfaceInfo> active() {
        return networkInfo.activeInterface().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/scope")
    public ScopeInfo scope() {
        return scope.describe();
    }

    @GetMapping("/summary")
    public NetworkSummaryResponse summary() {
        NetworkConfiguration cfg = networkInfo.configuration();
        DeviceStats stats = discovery.stats();
        MonitoringStatus mon = monitoring.status();

        String status;
        String reason;
        var gw = discovery.gatewayDevice();
        if (cfg.ipv4() == null) {
            status = "DOWN";
            reason = "This computer has no active network interface.";
        } else if (stats.total() <= 1 && mon.lastFullScanAt() == null) {
            status = "UNKNOWN";
            reason = "The first scan has not finished yet.";
        } else if (gw.isPresent() && "UNREACHABLE".equals(gw.get().state())) {
            status = "DOWN";
            reason = "The default gateway " + gw.get().ip() + " is not responding.";
        } else if (stats.critical() > 0) {
            status = "DEGRADED";
            reason = stats.critical() + " device(s) in critical condition.";
        } else if (stats.degraded() > 0) {
            status = "DEGRADED";
            reason = stats.degraded() + " device(s) with performance problems.";
        } else {
            status = "HEALTHY";
            reason = "No problems detected in the observed devices.";
        }
        return new NetworkSummaryResponse(cfg, stats, mon, scope.describe(), status, reason);
    }
}
