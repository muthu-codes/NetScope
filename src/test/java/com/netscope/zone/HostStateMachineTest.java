package com.netscope.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostStateMachineTest {

    @Test
    void oneFailureIsNotEnoughToDeclareDown() {
        var r = HostStateMachine.next(HostState.UP, 0, false, 3);
        assertEquals(HostState.UP, r.state());   // "possible failure" - previous state kept
        assertEquals(1, r.failures());
    }

    @Test
    void secondFailureIsDegraded() {
        var r = HostStateMachine.next(HostState.UP, 1, false, 3);
        assertEquals(HostState.DEGRADED, r.state());
    }

    @Test
    void thirdConsecutiveFailureIsDown() {
        var r = HostStateMachine.next(HostState.DEGRADED, 2, false, 3);
        assertEquals(HostState.DOWN, r.state());
        assertEquals(3, r.failures());
    }

    @Test
    void successAlwaysRecoversImmediately() {
        var r = HostStateMachine.next(HostState.DOWN, 9, true, 3);
        assertEquals(HostState.UP, r.state());
        assertEquals(0, r.failures());
    }
}
