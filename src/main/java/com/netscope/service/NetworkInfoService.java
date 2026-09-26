package com.netscope.service;

import com.netscope.model.GatewayInfo;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.NetworkInterfaceInfo;
import com.netscope.util.Cidr;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.MacAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Local network configuration: interfaces, active interface, IP/subnet, gateway, DNS. All read live. */
@Service
public class NetworkInfoService {

    private final GatewayService gateways;
    private final CommandExecutor exec;

    private NetworkConfiguration cachedConfig;
    private long cachedAt;

    public NetworkInfoService(GatewayService gateways, CommandExecutor exec) {
        this.gateways = gateways;
        this.exec = exec;
    }

    /** All interfaces with the "active" one marked. */
    public List<NetworkInterfaceInfo> listInterfaces() {
        String active = activeInterface().map(NetworkInterfaceInfo::name).orElse(null);
        List<NetworkInterfaceInfo> out = new ArrayList<>();
        for (NetworkInterfaceInfo i : enumerate()) out.add(i.withActive(i.name().equals(active)));
        return out;
    }

    /** The interface that carries the default route (its subnet contains the gateway). */
    public Optional<NetworkInterfaceInfo> activeInterface() {
        List<NetworkInterfaceInfo> all = enumerate();
        Optional<GatewayInfo> gw = gateways.defaultGateway();
        if (gw.isPresent()) {
            for (NetworkInterfaceInfo i : all) {
                if (!i.up() || i.loopback()) continue;
                for (String addr : i.ipv4()) {
                    if (Cidr.parse(addr).contains(gw.get().ip())) return Optional.of(i.withActive(true));
                }
            }
        }
        return all.stream()
                .filter(i -> i.up() && !i.loopback() && !i.virtual() && !i.ipv4().isEmpty() && !i.ipv4().get(0).startsWith("169.254"))
                .findFirst().map(i -> i.withActive(true));
    }

    public synchronized NetworkConfiguration configuration() {
        long now = System.currentTimeMillis();
        if (cachedConfig != null && now - cachedAt < 8_000) return cachedConfig;
        cachedConfig = buildConfiguration();
        cachedAt = System.currentTimeMillis();
        return cachedConfig;
    }

    public Set<String> localIpv4Addresses() {
        Set<String> ips = new TreeSet<>();
        for (NetworkInterfaceInfo i : enumerate()) for (String a : i.ipv4()) ips.add(a.substring(0, a.indexOf('/')));
        return ips;
    }

    private NetworkConfiguration buildConfiguration() {
        List<String> warnings = new ArrayList<>();
        Optional<GatewayInfo> gw = gateways.defaultGateway();
        Optional<NetworkInterfaceInfo> active = activeInterface();
        String hostname = localHostname();

        if (active.isEmpty()) {
            warnings.add("No active IPv4 network interface was found. Connect to Wi-Fi/Ethernet.");
            return new NetworkConfiguration(hostname, NetworkUtils.osName(), null, null, null, null, null, 0, null, null,
                    gw.map(GatewayInfo::ip).orElse(null), gw.map(GatewayInfo::mac).orElse(null),
                    List.of(), List.of(), false, Instant.now(), warnings);
        }

        NetworkInterfaceInfo nic = active.get();
        String gatewayIp = gw.map(GatewayInfo::ip).orElse(null);
        String chosen = nic.ipv4().get(0);
        if (gatewayIp != null) {
            for (String a : nic.ipv4()) if (Cidr.parse(a).contains(gatewayIp)) chosen = a;
        }
        String ip = chosen.substring(0, chosen.indexOf('/'));
        int prefix = Integer.parseInt(chosen.substring(chosen.indexOf('/') + 1));
        Cidr cidr = Cidr.of(IpAddressUtils.toLong(ip), prefix);

        if (ip.startsWith("169.254.")) warnings.add("The IP address is link-local (169.254.x.x): no DHCP lease was received.");
        if (gw.isEmpty()) warnings.add("No default gateway found: this computer has no route to other networks.");
        if (nic.virtual()) warnings.add("The active interface looks virtual (VM/VPN adapter); results reflect that network.");
        if (prefix < 22) warnings.add("Large local subnet (/" + prefix + "): a full sweep will take a while.");

        return new NetworkConfiguration(hostname, NetworkUtils.osName(), nic.name(), nic.displayName(), nic.type(),
                nic.mac(), ip, prefix, IpAddressUtils.prefixToNetmask(prefix), cidr.toString(),
                gatewayIp, gw.map(GatewayInfo::mac).orElse(null),
                dnsServers(gw.map(GatewayInfo::interfaceIndex).orElse(null)), nic.ipv6(), nic.up(), Instant.now(), warnings);
    }

    private List<NetworkInterfaceInfo> enumerate() {
        List<NetworkInterfaceInfo> out = new ArrayList<>();
        try {
            var en = NetworkInterface.getNetworkInterfaces();
            if (en == null) return out;
            for (NetworkInterface ni : Collections.list(en)) {
                try {
                    List<String> v4 = new ArrayList<>();
                    List<String> v6 = new ArrayList<>();
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress a = ia.getAddress();
                        if (a instanceof Inet4Address) v4.add(a.getHostAddress() + "/" + ia.getNetworkPrefixLength());
                        else if (a instanceof Inet6Address) {
                            String h = a.getHostAddress();
                            int pct = h.indexOf('%');
                            v6.add(pct > 0 ? h.substring(0, pct) : h);
                        }
                    }
                    boolean loop = ni.isLoopback();
                    boolean virtual = ni.isVirtual() || NetworkUtils.looksVirtual(ni.getName(), ni.getDisplayName());
                    out.add(new NetworkInterfaceInfo(ni.getName(), ni.getDisplayName(),
                            NetworkUtils.classifyInterface(ni.getName(), ni.getDisplayName(), loop),
                            MacAddressUtils.fromBytes(ni.getHardwareAddress()), ni.isUp(), loop, virtual,
                            ni.getMTU(), v4, v6, false));
                } catch (SocketException ignored) {
                    // interface disappeared while reading - skip it
                }
            }
        } catch (SocketException e) {
            return out;
        }
        return out;
    }

    private List<String> dnsServers(Integer interfaceIndex) {
        List<String> servers = new ArrayList<>();
        if (NetworkUtils.isWindows()) {
            String scope = interfaceIndex != null ? "-InterfaceIndex " + interfaceIndex + " " : "";
            String script = "Get-DnsClientServerAddress " + scope + "-AddressFamily IPv4 -ErrorAction SilentlyContinue | "
                    + "ForEach-Object { $_.ServerAddresses }";
            for (String line : exec.powershell(15000, script).output().split("\\R")) {
                String t = line.trim();
                if (IpAddressUtils.isValidIpv4(t) && !servers.contains(t)) servers.add(t);
            }
        } else {
            try {
                for (String line : Files.readAllLines(Path.of("/etc/resolv.conf"))) {
                    String t = line.trim();
                    if (t.startsWith("nameserver")) {
                        String ip = t.substring("nameserver".length()).trim();
                        if (IpAddressUtils.isValidIpv4(ip) && !servers.contains(ip)) servers.add(ip);
                    }
                }
            } catch (Exception ignored) {
                // resolv.conf not readable - leave empty
            }
        }
        return servers;
    }

    private static String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String env = System.getenv("COMPUTERNAME");
            if (env == null) env = System.getenv("HOSTNAME");
            return env == null ? "localhost" : env;
        }
    }
}
