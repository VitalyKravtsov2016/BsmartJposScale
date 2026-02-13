package com.bsmart;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Лёгкий smoke-тест для проверки базовой инициализации ScaleCLI без подключения к весам.
 */
public class ScaleCLISmokeTest {

    @Test
    public void testCanCreateCliAndLoadSettings() {
        ScaleCLI cli = new ScaleCLI();
        // должны уметь прочитать и изменить базовые параметры без падения
        String portBefore = cli.getPort();
        cli.setPort(portBefore != null ? portBefore : "COM1");
        assertNotNull(cli.getPort());
    }
}

