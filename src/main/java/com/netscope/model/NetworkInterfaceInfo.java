package com.netscope.model;

import java.util.List;

public record NetworkInterfaceInfo(String name, String displayName, String type, String mac, boolean up,
                                   boolean loopback, boolean virtual, int mtu,
                                   List<String> ipv4, List<String> ipv6, boolean active) {
    public NetworkInterfaceInfo withActive(boolean value) {
        return new NetworkInterfaceInfo(name, displayName, type, mac, up, loopback, virtual, mtu, ipv4, ipv6, value);
    }
}
