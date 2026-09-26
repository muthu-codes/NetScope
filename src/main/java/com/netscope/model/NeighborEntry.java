package com.netscope.model;

/** One row of the operating system's ARP / neighbour table. */
public record NeighborEntry(String ip, String mac, String state) {
}
