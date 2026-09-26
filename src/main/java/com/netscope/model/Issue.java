package com.netscope.model;

/** One detected problem on a device, with a plain-language explanation and a suggested fix. */
public record Issue(String code, String severity, String title, String detail, String recommendation) {
}
