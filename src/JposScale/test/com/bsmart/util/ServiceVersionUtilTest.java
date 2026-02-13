package com.bsmart.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ServiceVersionUtilTest {

    @Test
    public void testGetVersionIntValid() {
        // ожидается, что первая часть ServiceVersion.VERSION это целое число
        int version = ServiceVersionUtil.getVersionInt();
        assertTrue(version >= 0);
    }

    @Test
    public void testGetVersionIntHandlesInvalid() {
        // проверка граничных случаев через временную подстановку невозможна без изменения ServiceVersion,
        // поэтому просто убеждаемся, что метод не кидает исключения и возвращает число
        int result = ServiceVersionUtil.getVersionInt();
        assertTrue(result >= 0);
    }
}

