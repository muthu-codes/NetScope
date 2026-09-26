package com.netscope.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NmapParserTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <nmaprun>
              <host><status state="up"/>
                <address addr="10.20.30.5" addrtype="ipv4"/>
                <hostnames><hostname name="server1.lab"/></hostnames>
                <times srtt="1200" rttvar="100" to="5000"/>
                <ports>
                  <port protocol="tcp" portid="80"><state state="open"/><service name="http"/></port>
                  <port protocol="tcp" portid="443"><state state="closed"/><service name="https"/></port>
                </ports>
              </host>
              <host><status state="down"/>
                <address addr="10.20.30.6" addrtype="ipv4"/>
              </host>
            </nmaprun>
            """;

    @Test
    void parsesOnlyUpHosts() {
        var hosts = NmapParser.parse(XML);
        assertEquals(1, hosts.size());
        var h = hosts.get(0);
        assertEquals("10.20.30.5", h.ip());
        assertEquals("server1.lab", h.hostname());
        assertEquals(1.2, h.latencyMs());
        assertEquals(1, h.openPorts().size());
        assertEquals(80, h.openPorts().get(0).port());
    }

    @Test
    void emptyInputYieldsNoHosts() {
        assertTrue(NmapParser.parse(null).isEmpty());
        assertTrue(NmapParser.parse("").isEmpty());
    }
}
