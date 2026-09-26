package com.netscope.model;

/** The Wi-Fi network this computer is connected to right now (read from the OS). */
public record WifiConnection(String ssid, String bssid, Integer signalPercent, Integer dbm, Integer channel, String band,
                             String radioType, String authentication, Double receiveMbps, Double transmitMbps,
                             String qualityLabel) {
}
