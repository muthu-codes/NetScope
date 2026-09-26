package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a human-friendly name for an IP: reverse DNS first, then (Windows PCs only) the NetBIOS machine name.
 * Every lookup has a time limit so one slow DNS server cannot stall a scan.
 */
@Service
public class HostnameResolutionService {

    public record Resolved(String hostname, String source) {
    }

    private static final Pattern NETBIOS = Pattern.compile("^\\s*([^\\s<]+)\\s+<00>\\s+UNIQUE", Pattern.MULTILINE);

    private final NetScopeConfig config;
    private final CommandExecutor exec;
    private final ExecutorService dnsPool = Executors.newFixedThreadPool(48, NetworkUtils.daemonFactory("dns-lookup"));
    private final ExecutorService taskPool = Executors.newFixedThreadPool(24, NetworkUtils.daemonFactory("name-resolver"));

    public HostnameResolutionService(NetScopeConfig config, CommandExecutor exec) {
        this.config = config;
        this.exec = exec;
    }

    public CompletableFuture<Resolved> resolveAsync(String ip, boolean tryNetbios) {
        long limit = config.scan().hostnameTimeoutMs() * 2L + 4000L;
        return CompletableFuture.supplyAsync(() -> resolve(ip, tryNetbios), taskPool)
                .completeOnTimeout(null, limit, TimeUnit.MILLISECONDS)
                .exceptionally(e -> null);
    }

    public Resolved resolve(String ip, boolean tryNetbios) {
        Resolved dns = reverseDns(ip);
        if (dns != null) return dns;
        if (tryNetbios && config.scan().netbiosEnabled() && NetworkUtils.isWindows()) return netbios(ip);
        return null;
    }

    private Resolved reverseDns(String ip) {
        Future<String> f = dnsPool.submit(() -> InetAddress.getByName(ip).getCanonicalHostName());
        try {
            String name = f.get(config.scan().hostnameTimeoutMs(), TimeUnit.MILLISECONDS);
            if (name == null || name.isBlank() || name.equals(ip) || IpAddressUtils.isValidIpv4(name)) return null;
            if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
            return new Resolved(name, "reverse-dns");
        } catch (Exception e) {
            f.cancel(true);
            return null;
        }
    }

    private Resolved netbios(String ip) {
        if (!IpAddressUtils.isValidIpv4(ip)) return null;
        String out = exec.run(3500, "nbtstat", "-A", ip).output();
        return parseNetbios(out);
    }

    public static Resolved parseNetbios(String output) {
        if (output == null) return null;
        Matcher m = NETBIOS.matcher(output);
        return m.find() ? new Resolved(m.group(1), "netbios") : null;
    }
}
