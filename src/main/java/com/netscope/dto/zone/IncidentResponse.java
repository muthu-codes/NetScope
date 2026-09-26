package com.netscope.dto.zone;

import java.util.List;

public record IncidentResponse(String type, String severity, String title, List<String> affectedZones,
                               String commonDependency, List<String> evidence) {
}
