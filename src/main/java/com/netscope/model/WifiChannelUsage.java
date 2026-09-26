package com.netscope.model;

/** How busy one channel is. load counts overlapping 2.4 GHz neighbours (channels within +-4), weighted by signal. */
public record WifiChannelUsage(String band, int channel, int apCount, Integer strongestSignal, double load,
                               boolean connectedHere) {
}
