package com.netscope.model;

import java.util.List;

/** Description of the currently authorized scan scope - shown in the UI so it is never a secret. */
public record ScopeInfo(String mode, List<String> cidrs, long totalTargets, boolean authorizationConfirmed,
                        String authorizedBy, boolean valid, String message, List<String> warnings,
                        long estimatedMaxSeconds, String localCidr) {
}
