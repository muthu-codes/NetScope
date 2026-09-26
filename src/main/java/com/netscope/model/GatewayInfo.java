package com.netscope.model;

/** Default IPv4 gateway as reported by the routing table (lowest effective metric wins). */
public record GatewayInfo(String ip, String mac, Integer interfaceIndex, Integer metric, String source) {
    public GatewayInfo withMac(String newMac) {
        return new GatewayInfo(ip, newMac, interfaceIndex, metric, source);
    }
}
