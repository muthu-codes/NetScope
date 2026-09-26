package com.netscope.model;

import java.time.Instant;
import java.util.List;

/** Passive survey of the Wi-Fi networks in range. available=false means the OS did not give us any data (see message). */
public record WifiSurvey(Instant collectedAt, boolean available, String message, WifiConnection connection,
                         List<WifiNetwork> networks, List<WifiChannelUsage> channels, List<String> insights) {
}
