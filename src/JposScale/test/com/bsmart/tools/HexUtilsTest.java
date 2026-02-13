package com.bsmart.tools;

import static org.junit.Assert.*;

import org.junit.Test;

public class HexUtilsTest {

    @Test
    public void testToByteArrAndBack() {
        String hex = "0A1B2C";
        byte[] bytes = HexUtils.toByteArr(hex);
        assertEquals(3, bytes.length);
        assertEquals(hex, HexUtils.toHex2(bytes));
    }

    @Test
    public void testToHexByteShortInt() {
        assertEquals("00", HexUtils.toHex((byte) 0));
        assertEquals("0A", HexUtils.toHex((byte) 0x0A));

        short s = (short) 0x1234;
        assertEquals("1234", HexUtils.toHex(s));

        int i = 0x89ABCDEF;
        assertEquals("89ABCDEF", HexUtils.toHex(i));
    }

    @Test
    public void testToHexFFormatting() {
        byte[] bytes = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        String s = HexUtils.toHexF(bytes, bytes.length);
        assertTrue(s.contains("01"));
        assertTrue(s.contains("04"));
    }
}

