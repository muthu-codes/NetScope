package com.netscope.zone;

import java.util.List;

/** Read-only snapshot of a zone used by the correlation and topology logic (no database types). */
public record ZoneView(long id, String name, String cidr, ZoneType type, ZoneStatus status, String gateway,
                       Boolean gatewayUp, int up, int degraded, int down, List<String> path) {
}
