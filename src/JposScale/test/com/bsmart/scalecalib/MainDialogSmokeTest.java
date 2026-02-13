package com.bsmart.scalecalib;

import static org.junit.Assert.*;

import javax.swing.SwingUtilities;

import org.junit.Test;

/**
 * Простая проверка того, что диалог градуировки может быть создан и закрыт
 * в EDT без выброса исключений.
 *
 * Полноценное тестирование UI требует отдельной инфраструктуры и здесь не выполняется.
 */
public class MainDialogSmokeTest {

    @Test
    public void testCreateAndDisposeDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainDialog dialog = new MainDialog();
            assertNotNull(dialog);
            dialog.dispose();
        });
    }
}

