package com.netscope.config;

import com.netscope.exception.ScopeViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns NetScope's own exceptions into clean JSON errors. Everything else (404, missing parameters, ...)
 * is left to Spring's default handling so status codes stay correct.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScopeViolationException.class)
    public ResponseEntity<Map<String, Object>> scope(ScopeViolationException e) {
        return body(HttpStatus.FORBIDDEN, "SCOPE_VIOLATION", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("error", code);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
