package com.netscope.dto.zone;

import com.netscope.zone.ZoneTopologyBuilder;

import java.util.List;

public record ZoneTopologyResponse(List<ZoneTopologyBuilder.Node> nodes, List<ZoneTopologyBuilder.Edge> edges,
                                   String disclaimer) {
}
