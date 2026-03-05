package com.bsmart.tools;

import static org.junit.Assert.*;
import java.util.BitSet;
import org.junit.Test;

public class BitUtilsTest {

    @Test
    public void testSetBitAndTestBit() {
        int value = BitUtils.setBit(0) | BitUtils.setBit(3);
        assertTrue(BitUtils.testBit(value, 0));
        assertTrue(BitUtils.testBit(value, 3));
        assertFalse(BitUtils.testBit(value, 1));
    }

    @Test
    public void testFromByteArrayAndToByteArray() {
        byte[] bytes = new byte[] { (byte) 0b1010_0101 };
        BitSet bitSet = BitUtils.fromByteArray(bytes);
        // reverse operation should restore original bytes
        byte[] restored = BitUtils.toByteArray(bitSet);
        // only one byte should be significant for this test
        assertEquals(bytes[0], restored[restored.length - 1]);
    }

    @Test
    public void testSwapBitsSingleByte() {
        byte value = (byte) 0b1000_0001;
        int swapped = BitUtils.swapBits(value);
        assertEquals(0b1000_0001, swapped);
    }

    @Test
    public void testSwapBitsArrayOrderReversed() {
        byte[] data = new byte[] { 1, 2, 3 };
        byte[] swapped = BitUtils.swap(data);
        assertArrayEquals(new byte[] { 3, 2, 1 }, swapped);
    }
}

