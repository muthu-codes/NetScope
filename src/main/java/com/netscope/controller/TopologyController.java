package com.netscope.controller;

import com.netscope.dto.TopologyResponse;
import com.netscope.service.TopologyService;
import com.netscope.service.TracerouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/topology")
public class TopologyController {

    private final TopologyService topology;
    private final TracerouteService tracer;

    public TopologyController(TopologyService topology, TracerouteService tracer) {
        this.topology = topology;
        this.tracer = tracer;
    }

    @GetMapping
    public TopologyResponse topology() {
        return topology.build();
    }

    /** Raw traceroute results behind the routed part of the topology. */
    @GetMapping("/traces")
    public Collection<TracerouteService.TraceResult> traces() {
        return tracer.results();
    }
}
