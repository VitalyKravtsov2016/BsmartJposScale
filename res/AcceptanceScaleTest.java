package com.bsmart.jpos.scale;

import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.bsmart.jpos.scale.Pos2ProtocolEmulator;

import jpos.BaseControl;
import jpos.JposConst;
import jpos.JposException;
import jpos.ScaleConst;
import jpos.events.DataEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.JposEvent;
import jpos.events.OutputCompleteEvent;
import jpos.events.StatusUpdateEvent;
import jpos.services.EventCallbacks;

/**
 * Приемочные тесты ScaleService через com0com с эмулятором весов.
 *
 * Для запуска требуется: 
 * 1. Установить com0com (http://com0com.sourceforge.net/) 
 * 2. Создать пару портов (например, COM5 <-> COM6) 
 * 3. Подключить ScaleService к COM6 
 * 4. Запустить эмулятор на COM5
 */
public class AcceptanceScaleTest {

    private static final String EMULATOR_PORT = "COM5";
    private static final String SERVICE_PORT = "COM6";
    private static final String LOGICAL_NAME = "TestScale";

    /**
     * Реализация EventCallbacks для тестирования
     */
    private static class TestEventCallbacks implements EventCallbacks {

        private BlockingQueue<JposEvent> allEvents = new LinkedBlockingQueue<>();

        @Override
        public void fireDataEvent(DataEvent event) {
            allEvents.offer(event);
        }

        @Override
        public void fireDirectIOEvent(DirectIOEvent event) {
            allEvents.offer(event);
        }

        @Override
        public void fireErrorEvent(ErrorEvent event) {
            allEvents.offer(event);
        }

        @Override
        public void fireOutputCompleteEvent(OutputCompleteEvent event) {
            allEvents.offer(event);
        }

        @Override
        public void fireStatusUpdateEvent(StatusUpdateEvent event) {
            allEvents.offer(event);
        }

        @Override
        public BaseControl getEventSource() {
            return null;
        }

        public <T extends JposEvent> T waitForEvent(Class<T> eventType, long timeoutMillis) 
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;

            while (System.currentTimeMillis() < deadline) {
                JposEvent event = allEvents.poll(100, TimeUnit.MILLISECONDS);
                if (event != null && eventType.isAssignableFrom(event.getClass())) {
                    return eventType.cast(event);
                }
            }
            return null;
        }

        public void clearEvents() {
            allEvents.clear();
        }
    }

    private Pos2ProtocolEmulator emulator;
    private ScaleService service;
    private TestEventCallbacks callbacks;

    @Before
    public void setUp() throws Exception {
        // Запускаем эмулятор
        emulator = new Pos2ProtocolEmulator(EMULATOR_PORT);
        emulator.start();

        // Ждем готовности портов
        Thread.sleep(1000);

        // Создаем сервис
        service = new ScaleService();
        callbacks = new TestEventCallbacks();

        // Настраиваем параметры через системные свойства
        System.setProperty("jpos.entry." + LOGICAL_NAME + ".portName", SERVICE_PORT);
        System.setProperty("jpos.entry." + LOGICAL_NAME + ".protocol", "pos2");
        System.setProperty("jpos.entry." + LOGICAL_NAME + ".baudRate", "9600");
        System.setProperty("jpos.entry." + LOGICAL_NAME + ".timeout", "100");

        service.open(LOGICAL_NAME, callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setDataEventEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);

        // Пропускаем событие POWER_ONLINE
        callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        callbacks.clearEvents();
    }

    @After
    public void tearDown() throws Exception {
        if (service != null) {
            service.close();
        }
        if (emulator != null) {
            emulator.stop();
        }
    }

    @Test
    public void testReadWeight() throws Exception {
        // Устанавливаем вес на эмуляторе
        emulator.setWeight(1234);
        emulator.setStable(true);

        // Читаем вес в асинхронном режиме
        service.setAsyncMode(true);
        service.readWeight(null, 1000);

        DataEvent event = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти DataEvent", event);
        assertEquals(1234, event.getStatus());
    }

    @Test
    public void testUnstableWeight() throws Exception {
        service.setAsyncMode(true);

        // Сначала нестабильный вес
        emulator.setWeight(1000);
        emulator.setStable(false);

        // Через некоторое время стабилизируем
        new Thread(() -> {
            try {
                Thread.sleep(500);
                emulator.setStable(true);
            } catch (InterruptedException e) {
            }
        }).start();

        service.readWeight(null, 2000);

        DataEvent event = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти DataEvent", event);
        assertEquals(1000, event.getStatus());
    }

    @Test
    public void testZeroWeight() throws Exception {
        emulator.setWeight(0);
        emulator.setStable(true);

        service.setAsyncMode(true);
        service.setZeroValid(true);
        service.readWeight(null, 1000);

        DataEvent event = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти DataEvent с нулевым весом", event);
        assertEquals(0, event.getStatus());
    }

    @Test
    public void testOverweight() throws Exception {
        emulator.setWeight(40000); // больше максимального
        emulator.setStable(true);
        emulator.setOverweight(true);

        service.setAsyncMode(true);
        service.readWeight(null, 1000);

        // Должно прийти ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должен прийти ErrorEvent при перегрузе", errorEvent);
        assertEquals(ScaleConst.JPOS_ESCAL_OVERWEIGHT, errorEvent.getErrorCodeExtended());
    }

    @Test
    public void testSetTare() throws Exception {
        emulator.setWeight(500);
        emulator.setStable(true);

        service.setTareWeight(200);

        // Проверяем, что команда тары дошла до эмулятора
        Long tareValue = emulator.waitForTareCommand(2000, TimeUnit.MILLISECONDS);
        assertNotNull("Должна прийти команда тары", tareValue);
        assertEquals(200, tareValue.longValue());
    }

    @Test
    public void testZeroScale() throws Exception {
        emulator.setWeight(500);
        emulator.setStable(true);

        service.zeroScale();

        // Проверяем команду обнуления
        boolean zeroReceived = emulator.waitForZeroCommand(2000, TimeUnit.MILLISECONDS);
        assertTrue("Должна прийти команда обнуления", zeroReceived);
    }
}