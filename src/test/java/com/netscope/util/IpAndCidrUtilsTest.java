package com.netscope.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpAndCidrUtilsTest {

    @Test
    void validatesIpv4() {
        assertTrue(IpAddressUtils.isValidIpv4("10.114.32.73"));
        assertFalse(IpAddressUtils.isValidIpv4("256.1.1.1"));
        assertFalse(IpAddressUtils.isValidIpv4("10.1.1"));
        assertFalse(IpAddressUtils.isValidIpv4("10.1.1.1; whoami"));
        assertFalse(IpAddressUtils.isValidIpv4(null));
    }

    @Test
    void roundTripsLongs() {
        assertEquals("192.168.1.10", IpAddressUtils.fromLong(IpAddressUtils.toLong("192.168.1.10")));
    }

    @Test
    void detectsPrivateAndFilteredRanges() {
        assertTrue(IpAddressUtils.isPrivate("10.0.0.1"));
        assertTrue(IpAddressUtils.isPrivate("172.16.5.5"));
        assertTrue(IpAddressUtils.isPrivate("172.31.255.1"));
        assertFalse(IpAddressUtils.isPrivate("172.32.0.1"));
        assertTrue(IpAddressUtils.isPrivate("192.168.0.1"));
        assertTrue(IpAddressUtils.isPrivate("100.64.0.1"));
        assertFalse(IpAddressUtils.isPrivate("8.8.8.8"));
        assertFalse(IpAddressUtils.isUsableUnicast("224.0.0.251"));     // multicast
        assertFalse(IpAddressUtils.isUsableUnicast("255.255.255.255")); // broadcast
        assertFalse(IpAddressUtils.isUsableUnicast("127.0.0.1"));       // loopback
        assertFalse(IpAddressUtils.isUsableUnicast("169.254.10.10"));   // link-local
        assertTrue(IpAddressUtils.isUsableUnicast("10.114.32.55"));
    }

    @Test
    void cidrBasics() {
        Cidr c = Cidr.parse("10.114.32.73/24");
        assertEquals("10.114.32.0/24", c.toString());
        assertTrue(c.contains("10.114.32.200"));
        assertFalse(c.contains("10.114.33.1"));
        assertEquals(254, c.hostCount());
        assertEquals(254, c.hosts().size());
        assertEquals("10.114.32.1", c.hosts().get(0));
        assertEquals("10.114.32.254", c.hosts().get(253));
    }

    @Test
    void bigCidrSkipsDotZeroAndDotTwoFiftyFive() {
        Cidr c = Cidr.parse("10.10.0.0/16");
        assertTrue(c.isPrivate());
        assertFalse(c.hosts().contains("10.10.1.0"));
        assertFalse(c.hosts().contains("10.10.1.255"));
        assertTrue(c.hosts().contains("10.10.1.1"));
        assertEquals(65534, c.hostCount());
    }

    @Test
    void cidrContainsCidr() {
        assertTrue(Cidr.parse("10.0.0.0/16").contains(Cidr.parse("10.0.5.0/24")));
        assertFalse(Cidr.parse("10.0.5.0/24").contains(Cidr.parse("10.0.0.0/16")));
    }

    @Test
    void publicRangesAreNotPrivate() {
        assertFalse(Cidr.parse("8.8.8.0/24").isPrivate());
        assertFalse(Cidr.parse("10.0.0.0/7").isPrivate());   // spills out of 10/8
    }

    @Test
    void rejectsBadCidr() {
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("nonsense"));
    }
}
