package com.netscope.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacAddressUtilsTest {

    @Test
    void normalizesCommonFormats() {
        assertEquals("1A:03:0C:3A:4B:99", MacAddressUtils.normalize("1A-03-0C-3A-4B-99"));
        assertEquals("BC:C7:46:DC:7D:A9", MacAddressUtils.normalize("bc:c7:46:dc:7d:a9"));
        assertEquals("BC:C7:46:DC:7D:A9", MacAddressUtils.normalize("bcc7.46dc.7da9"));
        assertEquals("00:01:02:03:04:05", MacAddressUtils.normalize("0:1:2:3:4:5"));
        assertNull(MacAddressUtils.normalize("not-a-mac"));
        assertNull(MacAddressUtils.normalize(""));
    }

    @Test
    void filtersBroadcastMulticastAndEmpty() {
        assertFalse(MacAddressUtils.isUsable("FF-FF-FF-FF-FF-FF"));
        assertFalse(MacAddressUtils.isUsable("01-00-5E-00-00-FB"));   // IPv4 multicast
        assertFalse(MacAddressUtils.isUsable("00-00-00-00-00-00"));
        assertTrue(MacAddressUtils.isUsable("BC-C7-46-DC-7D-A9"));
    }

    @Test
    void detectsRandomizedMacs() {
        assertTrue(MacAddressUtils.isLocallyAdministered("1A-03-0C-3A-4B-99"));    // phone hotspot gateway style
        assertFalse(MacAddressUtils.isLocallyAdministered("BC-C7-46-DC-7D-A9"));
        assertEquals("BCC746", MacAddressUtils.ouiKey("BC-C7-46-DC-7D-A9"));
    }
}
