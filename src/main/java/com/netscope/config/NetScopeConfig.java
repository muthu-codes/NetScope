package com.netscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.List;

/**
 * Typed view of every "netscope.*" property in application.properties.
 * Nothing here is hardcoded to a specific network.
 */
@ConfigurationProperties(prefix = "netscope")
public record NetScopeConfig(
        @DefaultValue Scope scope,
        @DefaultValue Scan scan,
        @DefaultValue Monitor monitor,
        @DefaultValue Topology topology,
        @DefaultValue Health health,
        @DefaultValue Diagnostics diagnostics,
        @DefaultValue Retention retention,
        @DefaultValue Oui oui) {

    public record Scope(
            @DefaultValue("AUTO") String mode,
            @DefaultValue("") String cidrs,
            @DefaultValue("false") boolean authorizationConfirmed,
            @DefaultValue("") String authorizedBy,
            @DefaultValue("70000") int maxTargets) {

        public List<String> cidrList() {
            if (cidrs == null || cidrs.isBlank()) return List.of();
            return Arrays.stream(cidrs.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        public boolean configured() {
            return "CONFIGURED".equalsIgnoreCase(mode);
        }
    }

    public record Scan(
            @DefaultValue("64") int concurrency,
            @DefaultValue("800") int pingTimeoutMs,
            @DefaultValue("0") int delayBetweenProbesMs,
            @DefaultValue("1200") int hostnameTimeoutMs,
            @DefaultValue("true") boolean netbiosEnabled) {
    }

    public record Monitor(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("900") int fullScanIntervalSeconds,
            @DefaultValue("30") int refreshIntervalSeconds,
            @DefaultValue("5000") int tickMs,
            @DefaultValue("5000") int initialDelayMs,
            @DefaultValue("1800") int returnedAfterSeconds) {
    }

    public record Topology(
            @DefaultValue("true") boolean tracerouteEnabled,
            @DefaultValue("12") int maxHops,
            @DefaultValue("900") int traceRefreshSeconds,
            @DefaultValue("64") int maxTracedSubnets) {
    }

    public record Health(
            @DefaultValue("150") int highLatencyMs,
            @DefaultValue("300") int criticalLatencyMs,
            @DefaultValue("10") int lossWarningPercent,
            @DefaultValue("40") int lossCriticalPercent,
            @DefaultValue("50") int jitterWarningMs,
            @DefaultValue("20") int historySize) {
    }

    public record Diagnostics(
            @DefaultValue("8.8.8.8") String internetTarget,
            @DefaultValue("example.com") String dnsTestHost) {
    }

    public record Retention(@DefaultValue("7") int observationDays) {
    }

    public record Oui(@DefaultValue("") String file) {
    }
}
