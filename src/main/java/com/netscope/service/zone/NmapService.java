package com.netscope.service.zone;

import com.netscope.entity.NetworkZoneEntity;
import com.netscope.util.CommandExecutor;
import com.netscope.zone.NmapParser;
import com.netscope.zone.ZoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Optional deeper scan using nmap ("kali tool" integration), run ONLY against zones that are enabled + authorized.
 * NetScope's own ICMP/TCP checks (ZoneReachabilityService) never depend on nmap being installed.
 */
@Service
public class NmapService {

    private static final Logger log = LoggerFactory.getLogger(NmapService.class);

    private final CommandExecutor exec;
    private final NmapAvailability availability;

    public NmapService(CommandExecutor exec, NmapAvailability availability) {
        this.exec = exec;
        this.availability = availability;
    }

    public record ScanOutcome(boolean ranNmap, String reason, List<NmapParser.NmapHost> hosts) {
    }

    /** Ping sweep + TCP connect scan of the configured ports over the WHOLE zone CIDR in one nmap process. */
    public ScanOutcome scanZone(NetworkZoneEntity zone) {
        if (!zone.isEnabled() || !zone.isAuthorized())
            return new ScanOutcome(false, "Zone is not enabled/authorized.", List.of());
        if (!availability.available())
            return new ScanOutcome(false, "nmap is not installed on this host. Install nmap and it will be used automatically.", List.of());

        List<Integer> ports = ZoneValidator.portList(zone.getTcpPorts());
        String portArg = ports.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("80,443,22");
        int hostTimeoutMs = Math.max(500, zone.getTimeoutMs());
        long overallTimeout = 120_000L + (long) zone.getMaxDevices() * 300L;

        List<String> cmd = List.of("Z:\\Nmap\\nmap.exe",
                "-Pn",                       // do not rely on nmap's own host-discovery ping (we already do ICMP separately)
                "-sT",                        // plain TCP connect scan - needs no elevated privileges
                "-p", portArg,
                "--max-retries", "1",
                "--host-timeout", hostTimeoutMs + "ms",
                "-T4",
                "-oX", "-",                   // XML to stdout
                zone.getCidr());
        log.info("nmap scan starting: zone={} cidr={} ports={}", zone.getName(), zone.getCidr(), portArg);
        CommandExecutor.CommandResult r = exec.run(overallTimeout, cmd);
        if (r.startFailed()) return new ScanOutcome(false, "Could not start nmap.", List.of());
        if (r.timedOut()) return new ScanOutcome(true, "nmap timed out before finishing; partial results shown.", NmapParser.parse(r.output()));
        List<NmapParser.NmapHost> hosts = NmapParser.parse(r.output());
        log.info("nmap scan finished: zone={} hostsUp={}", zone.getName(), hosts.size());
        return new ScanOutcome(true, "ok", hosts);
    }

    public boolean available() {
        return availability.available();
    }

    public String version() {
        return availability.version();
    }
}
