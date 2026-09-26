package com.netscope.zone;

/**
 * Debounce logic: one timeout is NOT proof that a device is offline.
 *   success                       -> UP (failure counter reset)
 *   1st failure                   -> state unchanged ("possible failure")
 *   2nd failure (threshold > 2)   -> DEGRADED
 *   failures >= threshold         -> DOWN
 */
public final class HostStateMachine {

    public record Result(HostState state, int failures) {
    }

    private HostStateMachine() {
    }

    public static Result next(HostState previous, int failures, boolean success, int threshold) {
        if (success) return new Result(HostState.UP, 0);
        int limit = Math.max(1, threshold);
        int f = failures + 1;
        if (f >= limit) return new Result(HostState.DOWN, f);
        if (f >= 2) return new Result(HostState.DEGRADED, f);
        return new Result(previous == null ? HostState.UNKNOWN : previous, f);
    }
}
