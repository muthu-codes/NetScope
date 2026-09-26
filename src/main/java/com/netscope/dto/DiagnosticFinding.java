package com.netscope.dto;

public record DiagnosticFinding(String ip, String name, String healthStatus, int healthScore, String diagnosis) {
}
