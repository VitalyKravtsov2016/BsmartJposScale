package com.bsmart.tools;

import static org.junit.Assert.*;

import org.junit.Test;

public class StringParamsTest {

    @Test
    public void testSetGetAndDefault() {
        StringParams params = new StringParams();
        assertNull(params.get("missing"));
        assertEquals("default", params.get("missing", "default"));

        params.set("key", "123");
        assertEquals("123", params.get("key"));
        assertEquals("123", params.get("key", "default"));
    }

    @Test
    public void testGetIntAndDefault() throws Exception {
        StringParams params = new StringParams();
        params.set("intKey", "10");
        assertEquals(10, params.getInt("intKey"));

        assertEquals(5, params.getInt("missing", 5));
    }

    @Test(expected = Exception.class)
    public void testGetIntThrowsOnInvalid() throws Exception {
        StringParams params = new StringParams();
        params.set("bad", "abc");
        params.getInt("bad");
    }
}

