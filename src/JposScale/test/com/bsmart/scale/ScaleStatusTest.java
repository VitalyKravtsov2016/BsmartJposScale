package com.bsmart.scale;

import static org.junit.Assert.*;

import org.junit.Test;

public class ScaleStatusTest {

    @Test
    public void testFlagsMapping() {
        // set several bits: fixed (0), tare (3), stable (4), overweight (6)
        int value = 0;
        value |= 1 << 0; // fixed
        value |= 1 << 3; // tare
        value |= 1 << 4; // stable
        value |= 1 << 6; // overweight

        ScaleStatus status = new ScaleStatus(value);

        assertTrue(status.isFixed());
        assertTrue(status.isTareSet());
        assertTrue(status.isStable());
        assertTrue(status.isOverweight());

        assertFalse(status.isLowWeight());
        assertFalse(status.isADCNotResponding());
    }

    @Test
    public void testToStringContainsFlags() {
        ScaleStatus status = new ScaleStatus(1 << 4); // STABLE
        String s = status.toString();
        assertTrue(s.contains("STABLE"));
    }
}

