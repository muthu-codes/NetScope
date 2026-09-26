package com.netscope.controller;

import com.netscope.model.MonitoringStatus;
import com.netscope.model.ScanMode;
import com.netscope.service.MonitoringService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoring;

    public MonitoringController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/status")
    public MonitoringStatus status() {
        return monitoring.status();
    }

    @PostMapping("/start")
    public MonitoringStatus start() {
        monitoring.start();
        return monitoring.status();
    }

    @PostMapping("/stop")
    public MonitoringStatus stop() {
        monitoring.stop();
        return monitoring.status();
    }

    /** POST /api/monitoring/scan?mode=FULL|REFRESH - starts a scan in the background (202) or 409 if one is running. */
    @PostMapping("/scan")
    public ResponseEntity<MonitoringStatus> scan(@RequestParam(defaultValue = "FULL") String mode) {
        ScanMode m;
        try {
            m = ScanMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be FULL or REFRESH");
        }
        if (!monitoring.triggerAsync(m)) throw new IllegalStateException("A scan is already running.");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(monitoring.status());
    }
}
