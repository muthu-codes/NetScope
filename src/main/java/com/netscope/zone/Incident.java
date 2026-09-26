package com.netscope.zone;

import java.util.List;

/** An evidence-based finding. "commonDependency" is a possibility, never a proven root cause. */
public record Incident(String type, String severity, String title, List<String> affectedZones,
                       String commonDependency, List<String> evidence) {
}
