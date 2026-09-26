package com.netscope.controller;

import com.netscope.dto.DiagnosticResponse;
import com.netscope.model.PingStats;
import com.netscope.service.DiagnosticService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticController {

    private final DiagnosticService diagnostics;

    public DiagnosticController(DiagnosticService diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** GET /api/diagnostics/ping?ip=10.0.0.5&count=4  (IP must be inside the authorized scope) */
    @GetMapping("/ping")
    public PingStats ping(@RequestParam String ip, @RequestParam(defaultValue = "4") int count) {
        return diagnostics.ping(ip, clamp(count, 1, 20));
    }

    /** Runs the network-wide checks now (takes ~10-20 seconds). */
    @GetMapping("/network")
    public DiagnosticResponse network() {
        return diagnostics.runNetworkDiagnostics();
    }

    /** Result of the last network-wide run, if any. */
    @GetMapping("/network/last")
    public ResponseEntity<DiagnosticResponse> last() {
        return diagnostics.last().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/device")
    public DiagnosticResponse device(@RequestParam String ip,
                                     @RequestParam(defaultValue = "8") int count,
                                     @RequestParam(defaultValue = "false") boolean trace) {
        return diagnostics.diagnoseDevice(ip, clamp(count, 2, 20), trace);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
