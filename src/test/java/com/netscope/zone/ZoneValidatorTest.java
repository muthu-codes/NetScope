package com.netscope.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoneValidatorTest {

    private static ZoneRequest req(String name, String cidr, String type, Boolean authorized, String by) {
        return new ZoneRequest(name, cidr, "desc", type, true, authorized, by, null, null, null, false, null, null, null, null, null);
    }

    @Test
    void acceptsAuthorizedPrivateCidr() {
        var n = ZoneValidator.validate(req("CSE Lab", "10.20.30.0/24", "ROUTED", true, "IT admin"));
        assertEquals("10.20.30.0/24", n.cidr());
        assertEquals(ZoneType.ROUTED, n.type());
        assertTrue(n.authorized());
        assertEquals("IT admin", n.authorizedBy());
    }

    @Test
    void rejectsPublicCidr() {
        assertThrows(IllegalArgumentException.class, () -> ZoneValidator.validate(req("Bad", "8.8.8.0/24", "ROUTED", true, "x")));
    }

    @Test
    void rejectsCidrWithoutPrefix() {
        assertThrows(IllegalArgumentException.class, () -> ZoneValidator.validate(req("Bad", "10.0.0.5", "ROUTED", true, "x")));
    }

    @Test
    void rejectsTooLargeCidr() {
        assertThrows(IllegalArgumentException.class, () -> ZoneValidator.validate(req("Bad", "10.0.0.0/8", "ROUTED", true, "x")));
    }

    @Test
    void requiresAuthorizedByForNonLocalAuthorizedZones() {
        assertThrows(IllegalArgumentException.class, () -> ZoneValidator.validate(req("Lab", "10.20.30.0/24", "ROUTED", true, "")));
    }

    @Test
    void localZoneDoesNotNeedExplicitAuthorization() {
        var n = ZoneValidator.validate(req("Wifi", "192.168.0.0/24", "LOCAL", false, ""));
        assertTrue(n.authorized());
        assertEquals("local subnet", n.authorizedBy());
    }

    @Test
    void portsAreParsedAndDeduplicated() {
        assertEquals("80,443", ZoneValidator.parsePorts("80,443,80").replace(" ", ""));
    }

    @Test
    void rejectsUnknownMonitoringMethod() {
        assertThrows(IllegalArgumentException.class, () -> ZoneValidator.parseMethods("ICMP,ARP"));
    }
}
