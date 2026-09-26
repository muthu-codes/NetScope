package com.netscope.model;

/**
 * A relationship between two nodes.
 * confidence: OBSERVED (directly seen), INFERRED (derived from real data such as traceroute or IP subnet),
 *             ASSUMED (no data, best guess) or CONCEPTUAL (drawn only for readability).
 */
public record TopologyEdge(String id, String source, String target, String relation, String evidence,
                           String confidence, String label) {
}
