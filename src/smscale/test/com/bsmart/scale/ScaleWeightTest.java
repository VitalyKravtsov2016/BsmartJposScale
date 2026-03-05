package com.bsmart.scale;

import static org.junit.Assert.*;

import org.junit.Test;

public class ScaleWeightTest {

    @Test
    public void testGettersAndToString() {
        ScaleStatus status = new ScaleStatus(1 << 4); // STABLE
        ScaleWeight weight = new ScaleWeight(1500, 200, status);

        assertEquals(1500, weight.getWeight());
        assertEquals(200, weight.getTare());
        assertSame(status, weight.getStatus());

        String s = weight.toString();
        assertTrue(s.contains("Вес (стабилен"));
        assertTrue(s.contains("1.5"));
        assertTrue(s.contains("0.2"));
    }
}

