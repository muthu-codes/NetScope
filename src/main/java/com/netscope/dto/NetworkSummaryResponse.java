package com.netscope.dto;

import com.netscope.model.MonitoringStatus;
import com.netscope.model.NetworkConfiguration;
import com.netscope.model.ScopeInfo;

/** Everything the dashboard header/KPIs need in one call. networkStatus: HEALTHY, DEGRADED, DOWN, UNKNOWN. */
public record NetworkSummaryResponse(NetworkConfiguration configuration, DeviceStats devices, MonitoringStatus monitoring,
                                     ScopeInfo scope, String networkStatus, String networkStatusReason) {
}
