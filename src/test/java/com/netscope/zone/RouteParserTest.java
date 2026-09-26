package com.netscope.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteParserTest {

    @Test
    void parsesWindowsRouteTable() {
        String out = """
                ===========================================================================
                Network Destination        Netmask          Gateway       Interface  Metric
                          0.0.0.0          0.0.0.0    192.168.0.1    192.168.0.239     25
                    192.168.0.0    255.255.255.0         On-link    192.168.0.239    281
                """;
        var routes = RouteParser.parseWindows(out);
        assertTrue(routes.stream().anyMatch(r -> r.type().equals("DEFAULT") && "192.168.0.1".equals(r.nextHop())));
        assertTrue(routes.stream().anyMatch(r -> r.destination().equals("192.168.0.0/24") && r.type().equals("DIRECT")));
    }

    @Test
    void parsesLinuxRouteTable() {
        String out = "default via 10.0.0.1 dev eth0 metric 100\n10.0.0.0/24 dev eth0 proto kernel scope link src 10.0.0.5";
        var routes = RouteParser.parseLinux(out);
        assertTrue(routes.stream().anyMatch(r -> r.type().equals("DEFAULT") && "10.0.0.1".equals(r.nextHop())));
        assertTrue(routes.stream().anyMatch(r -> r.destination().equals("10.0.0.0/24") && r.type().equals("DIRECT")));
    }
}
