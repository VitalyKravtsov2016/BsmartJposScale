package com.bsmart;

import static org.junit.Assert.*;

import org.junit.Test;

public class EquipmentToolsTest {

    @Test
    public void testConvertDecToBCDAndBack() {
        int dec = 123456;
        int bcd = EquipmentTools.convertDecToBCD(dec);
        int restored = EquipmentTools.convertBCDToDec(bcd);
        assertEquals(dec, restored);
    }

    @Test
    public void testConvertLongBSDToDec() {
        byte[] data = new byte[] { 0x01, 0x23, 0x45 }; // условные данные
        int result = EquipmentTools.convertLongBSDToDec(data, 0, data.length);
        // просто убеждаемся, что метод отрабатывает без ошибок и возвращает неотрицательное значение
        assertTrue(result >= 0);
    }

    @Test
    public void testGetCRC() {
        byte[] data = new byte[] { 1, 2, 3, 4 };
        byte crc1 = EquipmentTools.getCRC(data, 0, data.length);
        byte crc2 = EquipmentTools.getCRC(data, 0, data.length);
        assertEquals(crc1, crc2);
    }
}

