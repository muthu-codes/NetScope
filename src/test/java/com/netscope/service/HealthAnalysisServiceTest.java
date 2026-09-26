package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.model.Device;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthAnalysisServiceTest {

    private final NetScopeConfig cfg = new NetScopeConfig(
            new NetScopeConfig.Scope("AUTO", "", false, "", 70000),
            new NetScopeConfig.Scan(64, 800, 0, 1200, true),
            new NetScopeConfig.Monitor(true, 900, 30, 5000, 5000, 1800),
            new NetScopeConfig.Topology(true, 12, 900, 64),
            new NetScopeConfig.Health(150, 300, 10, 40, 50, 20),
            new NetScopeConfig.Diagnostics("8.8.8.8", "example.com"),
            new NetScopeConfig.Retention(7),
            new NetScopeConfig.Oui(""));
    private final HealthAnalysisService health = new HealthAnalysisService(cfg);

    private Device reachable(double latency) {
        Device d = new Device("10.0.0.5");
        d.setState("REACHABLE");
        d.setLatencyMs(latency);
        for (int i = 0; i < 6; i++) d.pushSample(latency, 20);
        return d;
    }

    @Test
    void healthyDevice() {
        Device d = reachable(4);
        health.analyze(d);
        assertEquals("HEALTHY", d.getHealthStatus());
        assertEquals(100, d.getHealthScore());
        assertTrue(d.getDiagnosis().startsWith("Healthy"));
    }

    @Test
    void highLatencyIsDegraded() {
        Device d = reachable(220);
        health.analyze(d);
        assertEquals("DEGRADED", d.getHealthStatus());
        assertEquals("LATENCY_HIGH", d.getIssues().get(0).code());
    }

    @Test
    void criticalLatencyIsCritical() {
        Device d = reachable(450);
        health.analyze(d);
        assertEquals("CRITICAL", d.getHealthStatus());
    }

    @Test
    void packetLossIsDetected() {
        Device d = new Device("10.0.0.6");
        d.setState("REACHABLE");
        d.setLatencyMs(5.0);
        for (int i = 0; i < 10; i++) d.pushSample(i % 2 == 0 ? 5.0 : null, 20);     // 50% loss
        health.analyze(d);
        assertEquals(50.0, d.getPacketLossPercent());
        assertEquals("CRITICAL", d.getHealthStatus());
    }

    @Test
    void offlineGatewayIsCritical() {
        Device d = new Device("10.0.0.1");
        d.setGateway(true);
        d.setState("UNREACHABLE");
        d.setConsecutiveFailures(3);
        health.analyze(d);
        assertEquals("OFFLINE", d.getHealthStatus());
        assertEquals("CRITICAL", d.getIssues().get(0).severity());
    }

    @Test
    void arpOnlyIsLimitedNotBroken() {
        Device d = new Device("10.0.0.7");
        d.setState("ARP_ONLY");
        health.analyze(d);
        assertEquals("LIMITED", d.getHealthStatus());
        assertEquals("ICMP_BLOCKED", d.getIssues().get(0).code());
    }

    @Test
    void latencyBands() {
        assertEquals("OK", health.latencyBand(20.0));
        assertEquals("HIGH", health.latencyBand(200.0));
        assertEquals("CRITICAL", health.latencyBand(350.0));
    }
}
