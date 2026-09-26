package com.netscope.service;

import com.netscope.model.WifiChannelUsage;
import com.netscope.model.WifiConnection;
import com.netscope.model.WifiNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WifiSurveyServiceTest {

    private static final String NETSH = """
            Interface name : Wi-Fi
            There are 3 networks currently visible.

            SSID 1 : College-Staff
                Network type            : Infrastructure
                Authentication          : WPA2-Enterprise
                Encryption              : CCMP
                BSSID 1                 : 50:c7:bf:11:22:33
                     Signal             : 82%
                     Radio type         : 802.11ac
                     Band               : 5 GHz
                     Channel            : 36
                     Basic rates (Mbps) : 12 24
                BSSID 2                 : 50:c7:bf:11:22:34
                     Signal             : 60%
                     Radio type         : 802.11n
                     Band               : 2.4 GHz
                     Channel            : 6

            SSID 2 : Guest-Open
                Network type            : Infrastructure
                Authentication          : Open
                Encryption              : None
                BSSID 1                 : 1a:03:0c:3a:4b:99
                     Signal             : 45%
                     Radio type         : 802.11n
                     Channel            : 11

            SSID 3 :
                Network type            : Infrastructure
                Authentication          : WPA3-Personal
                Encryption              : CCMP
                BSSID 1                 : bc:c7:46:dc:7d:a9
                     Signal             : 30%
                     Radio type         : 802.11ax
                     Band               : 5 GHz
                     Channel            : 149
            """;

    private static final String INTERFACES = """
            There is 1 interface on the system:

                Name                   : Wi-Fi
                Description            : Intel(R) Wi-Fi 6 AX201 160MHz
                State                  : connected
                SSID                   : College-Staff
                BSSID                  : 50:c7:bf:11:22:33
                Network type           : Infrastructure
                Radio type             : 802.11ac
                Authentication         : WPA2-Enterprise
                Channel                : 36
                Receive rate (Mbps)    : 433.3
                Transmit rate (Mbps)   : 390
                Signal                 : 82%
            """;

    @Test
    void parsesNetshNetworks() {
        List<WifiNetwork> n = WifiSurveyService.parseWindowsNetworks(NETSH);
        assertEquals(4, n.size());
        assertEquals("College-Staff", n.get(0).ssid());
        assertEquals("50:C7:BF:11:22:33", n.get(0).bssid());
        assertEquals(82, n.get(0).signalPercent().intValue());
        assertEquals(-59, n.get(0).dbm().intValue());
        assertEquals("5 GHz", n.get(0).band());
        assertEquals("WPA2", n.get(0).securityLevel());
        assertEquals("2.4 GHz", n.get(1).band());
        assertEquals("OPEN", n.get(2).securityLevel());
        assertTrue(n.get(2).insecure());
        assertEquals("2.4 GHz", n.get(2).band());            // no Band line: derived from channel 11
        assertEquals("", n.get(3).ssid());                   // hidden network
        assertEquals("WPA3", n.get(3).securityLevel());
        assertFalse(n.get(3).insecure());
    }

    @Test
    void parsesNetshInterface() {
        WifiConnection c = WifiSurveyService.parseWindowsInterface(INTERFACES);
        assertEquals("College-Staff", c.ssid());
        assertEquals("50:C7:BF:11:22:33", c.bssid());
        assertEquals(36, c.channel().intValue());
        assertEquals(433.3, c.receiveMbps().doubleValue());
        assertEquals("Excellent", c.qualityLabel());
        assertNull(WifiSurveyService.parseWindowsInterface("    Name : Wi-Fi\n    State : disconnected\n"));
    }

    @Test
    void parsesNmcli() {
        String out = "*:Home Net:AA\\:BB\\:CC\\:DD\\:EE\\:FF:6:2437 MHz:78:WPA2\n:Cafe:11\\:22\\:33\\:44\\:55\\:66:36:5180 MHz:40:--\n";
        WifiSurveyService.NmcliParsed p = WifiSurveyService.parseNmcli(out);
        assertEquals(2, p.networks().size());
        assertEquals("AA:BB:CC:DD:EE:FF", p.networks().get(0).bssid());
        assertEquals("2.4 GHz", p.networks().get(0).band());
        assertEquals("OPEN", p.networks().get(1).securityLevel());
        assertEquals("5 GHz", p.networks().get(1).band());
        assertEquals("Home Net", p.connection().ssid());
    }

    @Test
    void channelUsageAndOverlap() {
        List<WifiNetwork> n = WifiSurveyService.parseWindowsNetworks(NETSH);
        List<WifiChannelUsage> u = WifiSurveyService.channelUsage(n, WifiSurveyService.parseWindowsInterface(INTERFACES));
        assertEquals(13 + 2, u.size());                      // channels 1-13 plus 5 GHz channels 36 and 149
        WifiChannelUsage ch6 = u.stream().filter(x -> x.band().equals("2.4 GHz") && x.channel() == 6).findFirst().orElseThrow();
        assertEquals(1, ch6.apCount());
        WifiChannelUsage ch36 = u.stream().filter(x -> x.channel() == 36).findFirst().orElseThrow();
        assertTrue(ch36.connectedHere());
        // channel 6 hears itself fully; channel 1 barely overlaps with channel 6 (5 apart) -> no load from it
        assertTrue(WifiSurveyService.overlapLoad(n, 6) > WifiSurveyService.overlapLoad(n, 1));
    }

    @Test
    void detectsWindowsProblems() {
        assertTrue(WifiSurveyService.windowsProblem("The requested operation requires elevation.").contains("Location"));
        assertTrue(WifiSurveyService.windowsProblem("There is no wireless interface on the system.").contains("No Wi-Fi adapter"));
        assertNull(WifiSurveyService.windowsProblem(NETSH));
    }
}
