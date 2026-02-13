package com.bsmart.tools;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.Test;

public class ToolsTest {

    @Test
    public void testConcat() {
        byte[] a = new byte[] { 1, 2 };
        byte[] b = new byte[] { 3, 4 };
        byte[] result = Tools.concat(a, b);
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, result);
    }

    @Test
    public void testLongToBytesAndBack() {
        long value = 0x0102030405060708L;
        byte[] bytes = Tools.longToBytes(value);
        long restored = Tools.bytesToLong(bytes);
        assertEquals(value, restored);
    }

    @Test
    public void testAdditionStringRightPadding() {
        String s = Tools.additionString("AB", '0', 4, false);
        assertEquals("AB00", s);
    }

    @Test
    public void testAdditionStringLeftPadding() {
        String s = Tools.additionString("AB", '0', 4, true);
        assertEquals("00AB", s);
    }

    @Test
    public void testGetBytesSubArray() {
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        byte[] part = Tools.getBytes(data, 1, 3);
        assertArrayEquals(new byte[] { 2, 3, 4 }, part);
    }

    @Test
    public void testSwapBytesShort() {
        short v = (short) 0x1234;
        short swapped = Tools.swapBytes(v);
        assertEquals((short) 0x3412, swapped);
    }

    @Test
    public void testGetLinesReadsLines() {
        String text = "line1\r\nline2\r\nlast";
        ByteArrayInputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        ArrayList<String> lines = Tools.getLines(in, "UTF-8");
        assertEquals(3, lines.size());
        assertEquals("line1", lines.get(0));
        assertEquals("line2", lines.get(1));
        assertEquals("last", lines.get(2));
    }

    @Test
    public void testRemoveUnreadableChars() {
        String s = "A" + (char) 5 + "B";
        String res = Tools.removeUnreadableChars(s);
        assertTrue(res.startsWith("A"));
        assertTrue(res.contains("#05"));
        assertTrue(res.endsWith("B"));
    }
}

