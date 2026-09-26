package com.netscope.scheduler;

import com.netscope.service.MonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Wakes the monitoring engine every few seconds; the engine decides if a scan is actually due. */
@Component
public class NetworkMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(NetworkMonitoringScheduler.class);

    private final MonitoringService monitoring;

    public NetworkMonitoringScheduler(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @Scheduled(fixedDelayString = "${netscope.monitor.tick-ms:5000}", initialDelayString = "${netscope.monitor.initial-delay-ms:5000}")
    public void tick() {
        try {
            monitoring.tick();
        } catch (Exception e) {
            log.warn("Scheduler tick failed: {}", e.getMessage());
        }
    }
}
