package com.netscope.service.zone;

import com.netscope.util.CommandExecutor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Detects once, at startup, whether the "nmap" binary is on PATH. Nmap is optional - NetScope works without it. */
@Component
public class NmapAvailability {

    private static final Logger log = LoggerFactory.getLogger(NmapAvailability.class);

    private final CommandExecutor exec;
    private volatile boolean available;
    private volatile String version = "";

    public NmapAvailability(CommandExecutor exec) {
        this.exec = exec;
    }

    @PostConstruct
    void detect() {
        try {
            CommandExecutor.CommandResult r = exec.run(4000, "Z:\\Nmap\\nmap.exe", "-V");
            available = r.ok() && r.output() != null && r.output().toLowerCase().contains("nmap");
            if (available) {
                String first = r.output().lines().findFirst().orElse("nmap").trim();
                version = first;
                log.info("nmap detected: {}", first);
            } else {
                log.info("nmap not found on PATH - advanced port/service scanning will be unavailable (ICMP/TCP checks still work).");
            }
        } catch (Exception e) {
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    public String version() {
        return version;
    }
}
