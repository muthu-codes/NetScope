package com.netscope.scheduler;

import com.netscope.dto.zone.ZoneResponse;
import com.netscope.service.zone.ZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Wakes every few seconds and starts a scan for any enabled+authorized zone whose interval has elapsed. */
@Component
public class ZoneMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZoneMonitoringScheduler.class);

    private final ZoneService zoneService;

    public ZoneMonitoringScheduler(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 8000)
    public void tick() {
        try {
            for (ZoneResponse z : zoneService.list()) {
                if (!z.enabled() || !z.authorized() || z.zoneType().equals("LOCAL")) continue;
                if (zoneService.isRunning(z.id())) continue;
                boolean due = z.lastScanAt() == null
                        || Duration.between(z.lastScanAt(), Instant.now()).getSeconds() >= z.intervalSeconds();
                if (due) zoneService.startScan(z.id());
            }
        } catch (Exception e) {
            log.warn("Zone scheduler tick failed: {}", e.getMessage());
        }
    }
}
