package com.netscope.model;

import java.time.Instant;
import java.util.List;

/** What this computer knows about its own network connection. Every value is read live from the OS. */
public record NetworkConfiguration(String hostname, String os, String interfaceName, String interfaceDisplayName,
                                   String interfaceType, String mac, String ipv4, int prefixLength, String netmask,
                                   String networkCidr, String gatewayIp, String gatewayMac,
                                   List<String> dnsServers, List<String> ipv6, boolean interfaceUp,
                                   Instant collectedAt, List<String> warnings) {
}
