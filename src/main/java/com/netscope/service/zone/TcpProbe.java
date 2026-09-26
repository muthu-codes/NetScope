package com.netscope.service.zone;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** A plain TCP connect check - proves a service is accepting connections without touching its contents. */
public final class TcpProbe {

    public record PortResult(int port, boolean open, Double connectMs) {
    }

    private TcpProbe() {
    }

    public static List<PortResult> check(String ip, List<Integer> ports, int timeoutMs) {
        List<PortResult> out = new ArrayList<>();
        for (int port : ports) {
            long start = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), timeoutMs);
                out.add(new PortResult(port, true, Math.round((System.nanoTime() - start) / 1_000_00.0) / 10.0));
            } catch (IOException e) {
                out.add(new PortResult(port, false, null));
            }
        }
        return out;
    }
}
