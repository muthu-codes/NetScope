package com.netscope.dto.zone;

import java.util.List;

public record NmapScanResponse(boolean ranNmap, String message, int hostsUp, List<Host> hosts) {
    public record Host(String ip, String hostname, Double latencyMs, List<Port> openPorts) {
    }

    public record Port(int port, String service) {
    }
}
