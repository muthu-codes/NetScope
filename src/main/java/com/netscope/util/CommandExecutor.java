package com.netscope.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a READ-ONLY operating system command (ping, tracert, arp, PowerShell Get-* ...) with a hard timeout.
 * Commands are always passed as an argument list - never through a shell - so user input cannot inject commands.
 */
@Component
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    public record CommandResult(int exitCode, String output, boolean timedOut, boolean startFailed) {
        public boolean ok() {
            return !timedOut && !startFailed && exitCode == 0;
        }
    }

    public CommandResult run(long timeoutMs, String... command) {
        return run(timeoutMs, Arrays.asList(command));
    }

    public CommandResult run(long timeoutMs, List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.debug("Could not start {}: {}", command.get(0), e.getMessage());
            return new CommandResult(-1, "", false, true);
        }

        StringBuffer out = new StringBuffer();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            } catch (IOException ignored) {
                // process was destroyed - fine
            }
        });

        boolean finished = false;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!finished) process.destroyForcibly();
        try {
            reader.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = finished ? process.exitValue() : -1;
        return new CommandResult(code, out.toString(), !finished, false);
    }

    /** Runs a PowerShell script (Windows only). */
    public CommandResult powershell(long timeoutMs, String script) {
        return run(timeoutMs, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
    }
}
