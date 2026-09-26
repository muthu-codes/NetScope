package com.netscope.model;

/**
 * One access-point radio (BSSID) heard by this computer's Wi-Fi adapter.
 * securityLevel: OPEN, WEP, WPA, WPA2, WPA3, OWE or UNKNOWN. insecure = OPEN, WEP or WPA (v1).
 * privateBssid = locally administered (randomized) MAC, typical of phone hotspots and virtual SSIDs.
 */
public record WifiNetwork(String ssid, String bssid, Integer signalPercent, Integer dbm, Integer channel, String band,
                          String radioType, String authentication, String encryption, String securityLevel,
                          boolean insecure, String vendor, boolean privateBssid, boolean connected) {

    public WifiNetwork enriched(String newVendor, boolean isPrivate, boolean isConnected) {
        return new WifiNetwork(ssid, bssid, signalPercent, dbm, channel, band, radioType, authentication, encryption,
                securityLevel, insecure, newVendor, isPrivate, isConnected);
    }
}
