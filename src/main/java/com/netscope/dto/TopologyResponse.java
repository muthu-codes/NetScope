package com.netscope.dto;

import com.netscope.model.TopologyEdge;
import com.netscope.model.TopologyNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** kind is always OBSERVED_INFERRED here: NetScope does not claim physical switch/cable topology. */
public record TopologyResponse(Instant generatedAt, String kind, String disclaimer, List<TopologyNode> nodes,
                               List<TopologyEdge> edges, Map<String, Object> summary, List<String> notes) {
}
