package com.netscope.service;

import com.netscope.config.NetScopeConfig;
import com.netscope.exception.ScopeViolationException;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.ScopeInfo;
import com.netscope.util.Cidr;
import com.netscope.util.IpAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The safety gate. NetScope only probes addresses that this class approves:
 *  - AUTO: the subnet this computer is connected to.
 *  - CONFIGURED: CIDRs from application.properties. Anything beyond the local subnet needs
 *    netscope.scope.authorization-confirmed=true. Public address space is never accepted.
 */
@Service
public class ScopeService {

    private static final Logger log = LoggerFactory.getLogger(ScopeService.class);

    private final NetScopeConfig config;
    private final NetworkInfoService networkInfo;
    private volatile String lastAudit = "";

    public ScopeService(NetScopeConfig config, NetworkInfoService networkInfo) {
        this.config = config;
        this.networkInfo = networkInfo;
    }

    /** Approved CIDR blocks, or a ScopeViolationException explaining why scanning is refused. */
    public List<Cidr> resolveScopes() {
        NetworkConfiguration cfg = networkInfo.configuration();
        Cidr local = localCidr(cfg);
        NetScopeConfig.Scope sc = config.scope();
        List<Cidr> result = new ArrayList<>();

        if (!sc.configured()) {
            if (local == null)
                throw new ScopeViolationException("No active network interface, so there is no local subnet to scan.");
            result.add(local);
        } else {
            List<String> raw = sc.cidrList();
            if (raw.isEmpty())
                throw new ScopeViolationException("netscope.scope.mode=CONFIGURED but netscope.scope.cidrs is empty.");
            for (String text : raw) {
                Cidr c;
                try {
                    c = Cidr.parse(text);
                } catch (IllegalArgumentException e) {
                    throw new ScopeViolationException("Invalid CIDR in netscope.scope.cidrs: '" + text + "'.");
                }
                if (!c.isPrivate())
                    throw new ScopeViolationException("Refusing to scan " + c + ": only private address ranges "
                            + "(10/8, 172.16/12, 192.168/16, 100.64/10) are allowed. NetScope never scans public networks.");
                boolean insideLocal = local != null && local.contains(c);
                if (!insideLocal && !sc.authorizationConfirmed())
                    throw new ScopeViolationException("Scope " + c + " is outside the local subnet. Set "
                            + "netscope.scope.authorization-confirmed=true only if you have written permission to scan it.");
                result.add(c);
            }
            if (local != null && result.stream().noneMatch(c -> c.contains(local))) result.add(local);
        }

        result = dedupe(result);
        long total = result.stream().mapToLong(Cidr::hostCount).sum();
        if (total > sc.maxTargets())
            throw new ScopeViolationException("Scope has " + total + " addresses, above netscope.scope.max-targets="
                    + sc.maxTargets() + ". Narrow the scope or raise the limit deliberately.");

        String audit = "mode=" + (sc.configured() ? "CONFIGURED" : "AUTO") + " scopes=" + result
                + " authorizedBy='" + sc.authorizedBy() + "' confirmed=" + sc.authorizationConfirmed();
        if (!audit.equals(lastAudit)) {
            lastAudit = audit;
            log.info("SCAN SCOPE AUTHORIZED: {}", audit);
        }
        return result;
    }

    public ScopeInfo describe() {
        NetScopeConfig.Scope sc = config.scope();
        NetworkConfiguration cfg = networkInfo.configuration();
        Cidr local = localCidr(cfg);
        List<String> warnings = new ArrayList<>();
        try {
            List<Cidr> scopes = resolveScopes();
            long total = scopes.stream().mapToLong(Cidr::hostCount).sum();
            long est = total * config.scan().pingTimeoutMs() / Math.max(1, config.scan().concurrency()) / 1000 + 5;
            if (total > 4096) warnings.add("Large scope: a full sweep can take up to about " + Math.max(1, est / 60) + " minutes.");
            if (sc.configured() && sc.authorizedBy().isBlank())
                warnings.add("Set netscope.scope.authorized-by so the audit log records who approved this scan.");
            return new ScopeInfo(sc.configured() ? "CONFIGURED" : "AUTO", scopes.stream().map(Cidr::toString).toList(), total,
                    sc.authorizationConfirmed(), sc.authorizedBy(), true, "Scope is valid.", warnings, est,
                    local == null ? null : local.toString());
        } catch (ScopeViolationException e) {
            return new ScopeInfo(sc.configured() ? "CONFIGURED" : "AUTO", List.of(), 0, sc.authorizationConfirmed(),
                    sc.authorizedBy(), false, e.getMessage(), warnings, 0, local == null ? null : local.toString());
        }
    }

    /** Throws unless the IP is a normal host address inside the approved scope. */
    public void assertInScope(String ip) {
        if (!IpAddressUtils.isValidIpv4(ip)) throw new IllegalArgumentException("'" + ip + "' is not a valid IPv4 address.");
        if (!IpAddressUtils.isUsableUnicast(ip))
            throw new IllegalArgumentException(ip + " is not a unicast host address (loopback/multicast/broadcast are not probed).");
        for (Cidr c : resolveScopes()) if (c.contains(ip)) return;
        NetworkConfiguration cfg = networkInfo.configuration();
        if (ip.equals(cfg.gatewayIp())) return;
        throw new ScopeViolationException(ip + " is outside the authorized scan scope.");
    }

    public static boolean inScope(List<Cidr> scopes, String ip) {
        for (Cidr c : scopes) if (c.contains(ip)) return true;
        return false;
    }

    private static Cidr localCidr(NetworkConfiguration cfg) {
        if (cfg == null || cfg.ipv4() == null) return null;
        return Cidr.of(IpAddressUtils.toLong(cfg.ipv4()), cfg.prefixLength());
    }

    private static List<Cidr> dedupe(List<Cidr> in) {
        List<Cidr> out = new ArrayList<>();
        for (Cidr c : in) {
            boolean covered = false;
            for (Cidr o : in) if (o != c && o.contains(c) && (o.prefix() < c.prefix() || in.indexOf(o) < in.indexOf(c))) covered = true;
            if (!covered && !out.contains(c)) out.add(c);
        }
        return out;
    }
}
