package com.netscope.service;

import com.netscope.model.GatewayInfo;
import com.netscope.model.NeighborEntry;
import com.netscope.model.PingStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Parses real-looking command output. These are the fragile parts of NetScope, so they are tested. */
class ParsingTest {

    private static final String WIN_PING_OK = """
            Pinging 10.114.32.55 with 32 bytes of data:
            Reply from 10.114.32.55: bytes=32 time=4ms TTL=64
            Reply from 10.114.32.55: bytes=32 time<1ms TTL=64

            Ping statistics for 10.114.32.55:
                Packets: Sent = 2, Received = 2, Lost = 0 (0% loss),
            """;

    private static final String WIN_PING_UNREACHABLE = """
            Pinging 10.114.32.99 with 32 bytes of data:
            Reply from 10.114.32.73: Destination host unreachable.
            Reply from 10.114.32.73: Destination host unreachable.
            """;

    private static final String LINUX_PING = """
            PING 10.0.0.1 (10.0.0.1) 56(84) bytes of data.
            64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=0.345 ms
            64 bytes from 10.0.0.1: icmp_seq=2 ttl=64 time=0.410 ms
            """;

    @Test
    void parsesWindowsPing() {
        List<PingService.Reply> r = PingService.parseReplies(WIN_PING_OK, "10.114.32.55");
        assertEquals(2, r.size());
        assertEquals(4.0, r.get(0).latencyMs());
        assertEquals(0.5, r.get(1).latencyMs());
        assertEquals(64, r.get(0).ttl());
    }

    @Test
    void ignoresDestinationUnreachableReplies() {
        assertTrue(PingService.parseReplies(WIN_PING_UNREACHABLE, "10.114.32.99").isEmpty());
        assertTrue(PingService.parseReplies(WIN_PING_UNREACHABLE, "10.114.32.73").isEmpty());
    }

    @Test
    void doesNotMixUpSimilarAddresses() {
        assertTrue(PingService.parseReplies(WIN_PING_OK, "10.114.32.5").isEmpty());
    }

    @Test
    void parsesLinuxPing() {
        List<PingService.Reply> r = PingService.parseReplies(LINUX_PING, "10.0.0.1");
        assertEquals(2, r.size());
        assertEquals(0.345, r.get(0).latencyMs(), 0.0001);
    }

    @Test
    void computesStats() {
        PingStats s = PingService.statsOf("10.0.0.1", 4,
                List.of(new PingService.Reply(10, 64), new PingService.Reply(20, 64), new PingService.Reply(10, 64)));
        assertEquals(3, s.received());
        assertEquals(25.0, s.lossPercent());
        assertEquals(10.0, s.minMs().doubleValue());
        assertEquals(20.0, s.maxMs().doubleValue());
        assertEquals(13.3, s.avgMs().doubleValue());
        assertEquals(10.0, s.jitterMs().doubleValue());
        assertEquals(100.0, PingService.statsOf("10.0.0.1", 4, List.of()).lossPercent());
    }

    @Test
    void parsesPowerShellNeighbours() {
        String text = """
                10.114.32.55|1A-03-0C-3A-4B-99|Reachable|12
                10.114.32.203|BC-C7-46-DC-7D-A9|Stale|12
                10.114.32.99||Unreachable|12
                224.0.0.22|01-00-5E-00-00-16|Permanent|12
                """;
        List<NeighborEntry> list = NeighborTableService.parseWindowsPowerShell(text);
        assertEquals(3, list.size());                      // the empty-MAC row is dropped by the parser
        assertEquals("1A:03:0C:3A:4B:99", list.get(0).mac());
        assertEquals("REACHABLE", list.get(0).state());
        assertTrue(NeighborTableService.isFresh("REACHABLE"));
        assertFalse(NeighborTableService.isFresh("STALE"));
    }

    @Test
    void parsesWindowsArpTable() {
        String text = """
                Interface: 10.114.32.73 --- 0x9
                  Internet Address      Physical Address      Type
                  10.114.32.55          1a-03-0c-3a-4b-99     dynamic
                  10.114.32.255         ff-ff-ff-ff-ff-ff     static
                  224.0.0.22            01-00-5e-00-00-16     static
                """;
        List<NeighborEntry> list = NeighborTableService.parseWindowsArp(text);
        assertEquals(3, list.size());
        assertEquals("10.114.32.55", list.get(0).ip());
    }

    @Test
    void parsesLinuxNeighbours() {
        List<NeighborEntry> l = NeighborTableService.parseLinuxNeigh(
                "10.0.0.1 dev eth0 lladdr 00:11:22:33:44:55 REACHABLE\n10.0.0.9 dev eth0  FAILED\n");
        assertEquals(1, l.size());
        assertEquals("00:11:22:33:44:55", l.get(0).mac());
    }

    @Test
    void picksGatewayFromRoutes() {
        List<GatewayInfo> g = GatewayService.parseWindowsRoutes("10.114.32.55|9|25\n0.0.0.0|4|281\n192.168.1.1|5|50\n");
        assertEquals(2, g.size());                         // on-link 0.0.0.0 is ignored
        assertEquals("10.114.32.55", g.get(0).ip());
        List<GatewayInfo> l = GatewayService.parseLinuxRoutes("default via 10.0.0.1 dev eth0 proto dhcp metric 100\n");
        assertEquals(100, l.get(0).metric().intValue());
    }

    @Test
    void parsesTracert() {
        String out = """
                Tracing route to 10.20.30.40 over a maximum of 12 hops

                  1    <1 ms    <1 ms    <1 ms  10.114.32.55
                  2     *        *        *     Request timed out.
                  3     3 ms     2 ms     2 ms  10.114.0.1
                  4     5 ms     4 ms     4 ms  10.20.30.40

                Trace complete.
                """;
        TracerouteService.TraceResult r = TracerouteService.parse("10.20.30.0/24", "10.20.30.40", out);
        assertEquals(4, r.hops().size());
        assertEquals("10.114.32.55", r.hops().get(0).ip());
        assertNull(r.hops().get(1).ip());
        assertEquals("10.114.0.1", r.hops().get(2).ip());
        assertTrue(r.reachedTarget());
    }

    @Test
    void parsesNetbiosName() {
        String out = """
                Node IpAddress: [10.0.0.5] Scope Id: []

                           NetBIOS Remote Machine Name Table

                       Name               Type         Status
                    ---------------------------------------------
                    LAB-PC-01      <00>  UNIQUE      Registered
                    WORKGROUP      <00>  GROUP       Registered
                """;
        assertEquals("LAB-PC-01", HostnameResolutionService.parseNetbios(out).hostname());
        assertNull(HostnameResolutionService.parseNetbios("Host not found."));
    }
}
