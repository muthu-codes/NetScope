package com.netscope.exception;

/** Thrown when something would be scanned/probed outside the authorized scope. */
public class ScopeViolationException extends RuntimeException {
    public ScopeViolationException(String message) {
        super(message);
    }
}
