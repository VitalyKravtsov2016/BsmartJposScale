package com.bsmart.jpos.scale;

import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

import com.bsmart.jpos.scale.Pos2ProtocolEmulator;
import com.bsmart.jpos.JposEntryBuilder;

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

    private static final String EMULATOR_PORT = "COM9";
    private static final String SERVICE_PORT = "COM10";
    private static final String LOGICAL_NAME = "TestScale";

    private Pos2ProtocolEmulator emulator;
    private ScaleService service;
    private TestEventCallbacks callbacks;

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

        // Создаем JposEntry с параметрами
        JposEntryBuilder entryBuilder = new JposEntryBuilder(LOGICAL_NAME);
        entryBuilder.addProperty("portName", SERVICE_PORT);
        entryBuilder.addProperty("protocol", "pos2");
        entryBuilder.addProperty("baudRate", "9600");
        entryBuilder.addProperty("timeout", "100");
        entryBuilder.addProperty("password", "30");
        
        service.setJposEntry(entryBuilder.build());
        
        service.open(LOGICAL_NAME, callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setDataEventEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);

        // Пропускаем событие POWER_ONLINE
        StatusUpdateEvent powerEvent = callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        assertNotNull("Должно быть событие POWER_ONLINE при включении", powerEvent);
        assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerEvent.getStatus());
        
        callbacks.clearEvents();
    }

    @After
    public void tearDown() throws Exception {
        if (service != null) {
            try {
                service.close();
            } catch (JposException e) {
                // ignore
            }
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
                // ignore
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
        
        // DataEvent не должно быть
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
        assertNull("Не должно быть DataEvent при перегрузе", dataEvent);
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

    @Test
    public void testMultipleReads() throws Exception {
        service.setAsyncMode(true);

        // Первый запрос
        emulator.setWeight(100);
        emulator.setStable(true);
        service.readWeight(null, 1000);

        DataEvent event1 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти первый DataEvent", event1);
        assertEquals(100, event1.getStatus());

        // Второй запрос
        emulator.setWeight(200);
        service.readWeight(null, 1000);

        DataEvent event2 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти второй DataEvent", event2);
        assertEquals(200, event2.getStatus());

        // Третий запрос
        emulator.setWeight(300);
        service.readWeight(null, 1000);

        DataEvent event3 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти третий DataEvent", event3);
        assertEquals(300, event3.getStatus());
    }

    @Test
    public void testAutoDisable() throws Exception {
        service.setAsyncMode(true);
        service.setAutoDisable(true);

        assertTrue("Устройство должно быть включено до чтения", service.getDeviceEnabled());

        emulator.setWeight(750);
        emulator.setStable(true);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должен прийти DataEvent", dataEvent);
        assertEquals(750, dataEvent.getStatus());

        // Ждем отключения устройства
        long deadline = System.currentTimeMillis() + 3000;
        boolean deviceDisabled = false;

        while (System.currentTimeMillis() < deadline) {
            if (!service.getDeviceEnabled()) {
                deviceDisabled = true;
                break;
            }
            Thread.sleep(100);
        }

        assertTrue("Устройство должно быть отключено после autoDisable", deviceDisabled);
    }

    @Test
    public void testStatusUpdateEvents() throws Exception {
        // Убеждаемся, что статусные события включены
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);

        // Нестабильный вес
        emulator.setWeight(500);
        emulator.setStable(false);

        StatusUpdateEvent unstableEvent = callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        assertNotNull("Должно быть событие о нестабильном весе", unstableEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE, unstableEvent.getStatus());

        callbacks.clearEvents();

        // Стабильный вес
        emulator.setStable(true);

        StatusUpdateEvent stableEvent = callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        assertNotNull("Должно быть событие о стабильном весе", stableEvent);
        assertEquals(ScaleConst.SCAL_SUE_STABLE_WEIGHT, stableEvent.getStatus());

        callbacks.clearEvents();

        // Нулевой вес
        emulator.setWeight(0);

        StatusUpdateEvent zeroEvent = callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        assertNotNull("Должно быть событие о нулевом весе", zeroEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_ZERO, zeroEvent.getStatus());
    }

    @Test
    public void testSyncReadWeight() throws Exception {
        emulator.setWeight(1234);
        emulator.setStable(true);

        // Синхронный режим
        service.setAsyncMode(false);
        int[] data = new int[1];
        service.readWeight(data, 2000);

        assertEquals("Вес должен быть прочитан синхронно", 1234, data[0]);
    }

    @Test
    public void testProperties() throws Exception {
        // Проверка свойств
        service.setZeroValid(true);
        assertTrue(service.getZeroValid());

        service.setZeroValid(false);
        assertFalse(service.getZeroValid());

        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        assertEquals(ScaleConst.SCAL_SN_ENABLED, service.getStatusNotify());

        service.setDataEventEnabled(false);
        assertFalse(service.getDataEventEnabled());

        service.setDataEventEnabled(true);
        assertTrue(service.getDataEventEnabled());

        service.setFreezeEvents(true);
        assertTrue(service.getFreezeEvents());

        service.setFreezeEvents(false);
        assertFalse(service.getFreezeEvents());
    }
}