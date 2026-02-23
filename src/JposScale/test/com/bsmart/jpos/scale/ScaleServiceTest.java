package com.bsmart.jpos.scale;

import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.bsmart.IDevice;
import com.bsmart.DeviceError;
import com.bsmart.jpos.scale.TestScaleSerial;
import com.bsmart.scale.EScale;
import com.bsmart.scale.ScaleSerial;
import com.bsmart.scale.ScaleWeight;
import com.bsmart.scale.ScaleStatus;
import com.bsmart.scale.DeviceMetrics;
import com.bsmart.tools.StringParams;

import jpos.BaseControl;
import jpos.JposConst;
import jpos.JposException;
import jpos.ScaleConst;
import jpos.events.DataEvent;
import jpos.events.JposEvent;
import jpos.events.StatusUpdateEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.OutputCompleteEvent;
import jpos.services.EventCallbacks;

/**
 * Полный тест ScaleService с использованием TestScaleSerial. Покрывает все
 * основные методы класса ScaleService.
 */
public class ScaleServiceTest {

    /**
     * Расширенный ScaleService для тестов, который возвращает TestScaleSerial
     * через createProtocol
     */
    private static class TestableScaleService extends ScaleService {

        private TestScaleSerial testScale;

        public void setTestScale(TestScaleSerial testScale) {
            this.testScale = testScale;
        }

        @Override
        protected ScaleSerial createProtocol(String protocol) throws Exception {
            return testScale;
        }

    }

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

        public <T extends JposEvent> T waitForEvent(Class<T> eventType, long timeout, TimeUnit unit)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

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

    private TestScaleSerial testScale;
    private TestableScaleService service;
    private TestEventCallbacks callbacks;

    @Before
    public void setUp() throws Exception {
        testScale = new TestScaleSerial();
        service = new TestableScaleService();
        service.setTestScale(testScale);
        callbacks = new TestEventCallbacks();

        // Базовая настройка тестового устройства
        testScale.setDeviceType(EScale.Pos2);
        testScale.setDeviceMetrics(new DeviceMetrics());
    }

    private void initService(boolean pollEnabled, boolean asyncMode) throws Exception {
        service.open("TestScale", callbacks);
        service.setPollEnabled(pollEnabled);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(asyncMode);

        // Пропускаем событие POWER_ONLINE при включении
        if (pollEnabled || asyncMode) {
            StatusUpdateEvent powerEvent = callbacks.waitForEvent(
                    StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
            if (powerEvent != null && powerEvent.getStatus() == JposConst.JPOS_SUE_POWER_ONLINE) {
                // OK
            }
        }
        callbacks.clearEvents();
    }

    private void initService(boolean pollEnabled) throws Exception {
        initService(pollEnabled, false);
    }

    private void cleanup() throws Exception {
        service.close();
        Thread.sleep(200);
    }

    // ======================== ТЕСТЫ АСИНХРОННОГО ЧТЕНИЯ ========================
    @Test
    public void testAsyncReadWeightProducesDataEvent() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1234, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithUnstableWeight() throws Exception {
        initService(false, true);

        // Сначала нестабильный вес
        testScale.setCurrentWeight(500, false, false);

        // Через некоторое время стабильный
        new Thread(() -> {
            try {
                Thread.sleep(500);
                testScale.setCurrentWeight(1234, true, false);
            } catch (InterruptedException e) {
            }
        }).start();

        service.readWeight(null, 2000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1234, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithTimeout() throws Exception {
        testScale.setCurrentWeight(500, false, false); // Всегда нестабильный

        initService(false, true);
        service.readWeight(null, 100);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent по таймауту", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithMultipleRequests() throws Exception {
        initService(false, true);

        // Первый запрос
        testScale.setCurrentWeight(100, true, false);
        service.readWeight(null, 1000);

        DataEvent event1 = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent для первого запроса", event1);
        assertEquals(100, event1.getStatus());

        // Второй запрос
        testScale.setCurrentWeight(200, true, false);
        service.readWeight(null, 1000);

        DataEvent event2 = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent для второго запроса", event2);
        assertEquals(200, event2.getStatus());

        // Третий запрос
        testScale.setCurrentWeight(300, true, false);
        service.readWeight(null, 1000);

        DataEvent event3 = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent для третьего запроса", event3);
        assertEquals(300, event3.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithInterruptedThread() throws Exception {
        testScale.setResponseDelay(2000); // Долгий ответ

        initService(false, true);
        callbacks.clearEvents();

        service.readWeight(null, 3000);
        Thread.sleep(100);
        service.setAsyncMode(false); // Прерываем поток

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("Не должно быть DataEvent после прерывания", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ СИНХРОННОГО ЧТЕНИЯ ========================
    @Test
    public void testSyncReadWeight() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, false);

        int[] data = new int[1];
        service.readWeight(data, 1000);

        assertEquals("Вес должен быть прочитан синхронно", 1234, data[0]);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithTimeout() throws Exception {
        testScale.setCurrentWeight(500, false, false);

        initService(false, false);

        int[] data = new int[1];
        service.readWeight(data, 100);

        assertEquals("Должен вернуться текущий вес по таймауту", 500, data[0]);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithZeroValid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, false);
        service.setZeroValid(true);

        int[] data = new int[1];
        service.readWeight(data, 1000);

        assertEquals("Нулевой вес должен быть принят", 0, data[0]);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithZeroInvalid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, false);
        service.setZeroValid(false);

        int[] data = new int[1];
        service.readWeight(data, 100);

        assertEquals("По таймауту вернется текущий вес", 0, data[0]);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithOverweight() throws Exception {
        initService(false, false);

        // Сначала нормальный вес
        testScale.setCurrentWeight(500, true, false);
        int[] data = new int[1];
        service.readWeight(data, 1000);
        assertEquals(500, data[0]);

        // Перегруз - должно быть исключение
        testScale.setCurrentWeight(2000, true, true);

        try {
            service.readWeight(data, 1000);
            fail("Должно быть выброшено JposException для перегруза");
        } catch (JposException e) {
            assertEquals("Код ошибки должен быть JPOS_E_EXTENDED",
                    JposConst.JPOS_E_EXTENDED, e.getErrorCode());
            assertEquals("Extended код ошибки должен быть JPOS_ESCAL_OVERWEIGHT",
                    ScaleConst.JPOS_ESCAL_OVERWEIGHT, e.getErrorCodeExtended());
        }

        cleanup();
    }

    // ======================== ТЕСТЫ НУЛЕВОГО ВЕСА ========================
    @Test
    public void testAsyncReadWeightWithZeroWeightAndZeroValid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, true);
        service.setZeroValid(true);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent с нулевым весом", dataEvent);
        assertEquals(0, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithZeroWeightAndZeroInvalid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, true);
        service.setZeroValid(false);
        service.readWeight(null, 500);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent по таймауту", dataEvent);
        assertEquals(0, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ПЕРЕГРУЗКИ ========================
    @Test
    public void testAsyncReadWeightWithOverweight() throws Exception {
        initService(false, true);

        // Нормальный вес
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть DataEvent для нормального веса", normalEvent);
        assertEquals(500, normalEvent.getStatus());

        callbacks.clearEvents();

        // Перегруз
        testScale.setCurrentWeight(2000, true, true);
        service.readWeight(null, 1000);

        // В асинхронном режиме должно прийти ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть ErrorEvent при перегрузе", errorEvent);

        // DataEvent не должно быть
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("Не должно быть DataEvent при перегрузе", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ AUTO DISABLE ========================
    @Test
    public void testAsyncReadWeightWithAutoDisable() throws Exception {
        initService(false, true);
        service.setAutoDisable(true);

        assertTrue("Устройство должно быть включено до чтения", service.getDeviceEnabled());

        testScale.setCurrentWeight(750, true, false);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(750, dataEvent.getStatus());

        // Проверка autoDisable
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

        cleanup();
    }

    // ======================== ТЕСТЫ ТАРЫ ========================
    @Test
    public void testAsyncReadWeightWithTare() throws Exception {
        testScale.setCurrentWeight(1000, true, false);

        initService(false, true);
        service.setTareWeight(200);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1000, dataEvent.getStatus());

        // Проверяем, что tara была вызвана
        Long tareValue = testScale.getLastTare();
        assertNotNull(tareValue);
        assertEquals(200, tareValue.longValue());

        cleanup();
    }

    @Test
    public void testGetTareWeight() throws Exception {
        initService(false);

        service.setTareWeight(500);
        assertEquals(500, service.getTareWeight());

        cleanup();
    }

    // ======================== ТЕСТЫ CAPABILITIES ========================
    @Test
    public void testCapabilities() throws Exception {
        service.open("TestScale", callbacks);

        assertFalse(service.getCapCompareFirmwareVersion());
        assertTrue(service.getCapStatusUpdate());
        assertFalse(service.getCapUpdateFirmware());
        assertFalse(service.getCapDisplay());
        assertFalse(service.getCapStatisticsReporting());
        assertFalse(service.getCapUpdateStatistics());
        assertFalse(service.getCapDisplayText());
        assertEquals(JposConst.JPOS_PR_STANDARD, service.getCapPowerReporting());
        assertFalse(service.getCapPriceCalculating());
        assertTrue(service.getCapTareWeight());

        // CapZeroScale зависит от типа весов
        testScale.setDeviceType(EScale.Pos2);
        assertTrue(service.getCapZeroScale());

        testScale.setDeviceType(EScale.Shtrih5);
        assertFalse(service.getCapZeroScale());

        service.close();
    }

    // ======================== ТЕСТЫ СОСТОЯНИЯ ========================
    @Test
    public void testStateTransitions() throws Exception {
        // Начальное состояние - CLOSED
        assertEquals(JposConst.JPOS_S_CLOSED, service.getState());

        // Open
        service.open("TestScale", callbacks);
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());
        assertFalse(service.getClaimed());

        // Claim
        service.claim(0);
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());
        assertTrue(service.getClaimed());

        // Enable
        service.setDeviceEnabled(true);
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());
        assertTrue(service.getDeviceEnabled());

        // Async mode
        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());

        // Disable
        service.setDeviceEnabled(false);
        assertFalse(service.getDeviceEnabled());
        assertFalse(service.getAsyncMode());

        // Release
        service.release();
        assertFalse(service.getClaimed());

        // Close
        service.close();
        assertEquals(JposConst.JPOS_S_CLOSED, service.getState());
    }

    // ======================== ТЕСТЫ POWER ========================
    @Test
    public void testPowerStateWithNotifyEnabled() throws Exception {
        initService(true);

        // Пропускаем событие POWER_ONLINE от включения
        StatusUpdateEvent powerOnEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие POWER_ONLINE при включении", powerOnEvent);
        assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerOnEvent.getStatus());

        callbacks.clearEvents();

        // Устанавливаем OFFLINE
        service.setPowerState(JposConst.JPOS_PS_OFFLINE);
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие POWER_OFFLINE", event);
        assertEquals(JposConst.JPOS_SUE_POWER_OFFLINE, event.getStatus());

        cleanup();
    }

    @Test
    public void testPowerStateWithNotifyDisabled() throws Exception {
        initService(true);
        callbacks.clearEvents();

        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);
        service.setPowerState(JposConst.JPOS_PS_ONLINE);

        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNull("Не должно быть событий при PowerNotify DISABLED", event);

        cleanup();
    }

    // ======================== ТЕСТЫ СТАТУСНЫХ СОБЫТИЙ ========================
    @Test
    public void testStatusUpdateEvents() throws Exception {
        initService(true, false);
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        callbacks.clearEvents();

        // Нестабильный вес
        testScale.setCurrentWeight(500, false, false);
        Thread.sleep(500);

        StatusUpdateEvent unstableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие о нестабильном весе", unstableEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE, unstableEvent.getStatus());

        callbacks.clearEvents();

        // Стабильный вес
        testScale.setCurrentWeight(500, true, false);
        Thread.sleep(500);

        StatusUpdateEvent stableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие о стабильном весе", stableEvent);
        assertEquals(ScaleConst.SCAL_SUE_STABLE_WEIGHT, stableEvent.getStatus());

        callbacks.clearEvents();

        // Нулевой вес
        testScale.setCurrentWeight(0, true, false);
        Thread.sleep(500);

        StatusUpdateEvent zeroEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие о нулевом весе", zeroEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_ZERO, zeroEvent.getStatus());

        cleanup();
    }

    @Test
    public void testStatusNotifyDisabled() throws Exception {
        initService(true, false);
        service.setStatusNotify(ScaleConst.SCAL_SN_DISABLED);
        callbacks.clearEvents();

        testScale.setCurrentWeight(500, false, false);
        Thread.sleep(1000);

        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 1, TimeUnit.SECONDS);
        assertNull("Не должно быть статусных событий при SCAL_SN_DISABLED", event);

        cleanup();
    }

    // ======================== ТЕСТЫ FREEZE EVENTS ========================
    @Test
    public void testFreezeEvents() throws Exception {
        initService(false, true);

        service.setFreezeEvents(true);
        service.setDataEventEnabled(true);
        callbacks.clearEvents();

        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("События не должны приходить при freezeEvents=true", dataEvent);

        service.setFreezeEvents(false);
        dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("После разморозки событие должно прийти", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ОБРАБОТКИ ОШИБОК УСТРОЙСТВА ========================
    @Test
    public void testDeviceErrorNoLink() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Нормальная работа
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull(normalEvent);
        assertEquals(500, normalEvent.getStatus());

        callbacks.clearEvents();

        // Ошибка ERROR_NOLINK
        testScale.setNextException(new DeviceError(IDevice.ERROR_NOLINK, "No link to device"));
        service.readWeight(null, 1000);

        Thread.sleep(500);
        assertEquals(JposConst.JPOS_PS_OFF_OFFLINE, service.getPowerState());

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        cleanup();
    }

    @Test
    public void testDeviceErrorGeneric() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Нормальная работа
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull(normalEvent);

        callbacks.clearEvents();

        // Общая ошибка
        testScale.setNextException(new RuntimeException("Generic error"));
        service.readWeight(null, 1000);

        assertEquals(JposConst.JPOS_PS_ONLINE, service.getPowerState());

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        cleanup();
    }

    @Test
    public void testDeviceErrorOther() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Нормальная работа
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull(normalEvent);

        callbacks.clearEvents();

        // Другая ошибка
        testScale.setNextException(new DeviceError(999, "Other error"));
        service.readWeight(null, 1000);

        assertEquals(JposConst.JPOS_PS_ONLINE, service.getPowerState());

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ СВОЙСТВ ========================
    @Test
    public void testProperties() throws Exception {
        service.open("TestScale", callbacks);

        service.setZeroValid(true);
        assertTrue(service.getZeroValid());
        service.setZeroValid(false);
        assertFalse(service.getZeroValid());

        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        assertEquals(ScaleConst.SCAL_SN_ENABLED, service.getStatusNotify());

        service.setDataEventEnabled(true);
        assertTrue(service.getDataEventEnabled());
        service.setDataEventEnabled(false);
        assertFalse(service.getDataEventEnabled());

        service.setFreezeEvents(true);
        assertTrue(service.getFreezeEvents());
        service.setFreezeEvents(false);
        assertFalse(service.getFreezeEvents());

        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());
        service.setAsyncMode(false);
        assertFalse(service.getAsyncMode());

        service.setAutoDisable(true);
        assertTrue(service.getAutoDisable());
        service.setAutoDisable(false);
        assertFalse(service.getAutoDisable());

        service.setPollEnabled(false);
        assertFalse(service.getPollEnabled());
        service.setPollEnabled(true);
        assertTrue(service.getPollEnabled());

        service.close();
    }

    // ======================== ТЕСТЫ ИНФОРМАЦИИ ОБ УСТРОЙСТВЕ ========================
    @Test
    public void testDeviceInfo() throws Exception {
        service.open("TestScale", callbacks);

        assertNotNull(service.getPhysicalDeviceDescription());
        assertNotNull(service.getPhysicalDeviceName());
        assertNotNull(service.getDeviceServiceDescription());
        assertTrue(service.getDeviceServiceVersion() > 0);

        service.close();
    }

    @Test
    public void testScaleSpecificInfo() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);

        assertEquals(2147483647, service.getMaximumWeight());
        assertEquals(ScaleConst.SCAL_WU_GRAM, service.getWeightUnit());

        int liveWeight = service.getScaleLiveWeight();
        assertTrue(liveWeight >= 0);

        cleanup();
    }

    // ======================== ТЕСТЫ ОЧИСТКИ ========================
    @Test
    public void testClearInput() throws Exception {
        initService(false, true);

        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 1000);

        // Даем время на обработку запроса
        Thread.sleep(100);

        service.clearInput();

        // Очищаем очередь событий после clearInput
        callbacks.clearEvents();

        // Ждем - событий быть не должно
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("После clearInput не должно быть событий", dataEvent);

        cleanup();
    }

    @Test
    public void testClearInputWithPendingRequests() throws Exception {
        testScale.setResponseDelay(500);
        initService(false, true);

        service.readWeight(null, 1000);
        service.readWeight(null, 1000);
        service.readWeight(null, 1000);

        // Даем время запросам попасть в очередь
        Thread.sleep(100);

        service.clearInput();

        // Очищаем очередь событий
        callbacks.clearEvents();

        // Ждем - событий быть не должно
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNull("Не должно быть событий после clearInput", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ ОШИБОК ========================
    @Test(expected = JposException.class)
    public void testReadWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.readWeight(null, 1000);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetTareWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setTareWeight(100);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testZeroScaleWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.zeroScale();
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetUnitPrice() throws Exception {
        initService(false);
        service.setUnitPrice(100);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testDirectIO() throws Exception {
        initService(false);
        service.directIO(0, null, null);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testCheckHealth() throws Exception {
        initService(false);
        service.checkHealth(0);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testReadWeightNotClaimed() throws Exception {
        service.open("TestScale", callbacks);
        service.readWeight(null, 1000);
        cleanup();
    }

    // ======================== ТЕСТЫ КОНКУРЕНТНОГО ДОСТУПА ========================
    @Test
    public void testConcurrentReadWeight() throws Exception {
        testScale.setCurrentWeight(500, true, false);
        initService(false, true);
        callbacks.clearEvents();

        Thread[] threads = new Thread[5];
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                try {
                    service.readWeight(null, 1000);
                    successCount.incrementAndGet();
                } catch (JposException e) {
                    fail("Не должно быть исключения");
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join(2000);
        }

        for (int i = 0; i < 5; i++) {
            DataEvent event = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
            assertNotNull("Должно быть получено DataEvent", event);
        }

        assertEquals(5, successCount.get());

        cleanup();
    }

    @Test
    public void testConcurrentSetProperties() throws Exception {
        testScale.setCurrentWeight(500, true, false);
        initService(false, true);
        callbacks.clearEvents();

        AtomicInteger errorCount = new AtomicInteger(0);

        Thread setter1 = new Thread(() -> {
            try {
                service.setZeroValid(true);
                service.setAutoDisable(true);
            } catch (JposException e) {
                errorCount.incrementAndGet();
            }
        });

        Thread setter2 = new Thread(() -> {
            try {
                service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
                service.setPollEnabled(false);
            } catch (JposException e) {
                errorCount.incrementAndGet();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                service.readWeight(null, 1000);
            } catch (JposException e) {
                errorCount.incrementAndGet();
            }
        });

        setter1.start();
        setter2.start();
        reader.start();

        setter1.join(1000);
        setter2.join(1000);
        reader.join(1000);

        assertEquals(0, errorCount.get());

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull(dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ РАЗНЫХ ПРОТОКОЛОВ ========================
    @Test
    public void testDifferentProtocols() throws Exception {
        testScale.setDeviceType(EScale.Pos2);
        service.open("TestScale", callbacks);
        assertTrue(service.getPhysicalDeviceDescription().contains("POS2"));
        service.close();

        testScale.setDeviceType(EScale.Shtrih5);
        service.open("TestScale", callbacks);
        assertTrue(service.getPhysicalDeviceDescription().contains("ШТРИХ5"));
        service.close();

        testScale.setDeviceType(EScale.Shtrih6);
        service.open("TestScale", callbacks);
        assertTrue(service.getPhysicalDeviceDescription().contains("ШТРИХ6"));
        service.close();
    }

    // ======================== ТЕСТЫ МНОЖЕСТВЕННЫХ СЛУШАТЕЛЕЙ ========================
    @Test
    public void testMultipleEventListeners() throws Exception {
        TestEventCallbacks secondCallbacks = new TestEventCallbacks();

        testScale.setCurrentWeight(500, true, false);

        service.open("TestScale", callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(true);
        service.setDataEventEnabled(true);

        service.readWeight(null, 1000);

        DataEvent event1 = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Первый слушатель должен получить событие", event1);

        DataEvent event2 = secondCallbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("Второй слушатель не должен получить событие", event2);

        cleanup();
    }

    // ======================== ТЕСТЫ ГРАНИЧНЫХ ЗНАЧЕНИЙ ========================
    @Test
    public void testExtremeWeightValues() throws Exception {
        testScale.setCurrentWeight(Integer.MAX_VALUE, true, false);

        initService(false, true);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(Integer.MAX_VALUE, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testNegativeWeight() throws Exception {
        testScale.setCurrentWeight(-100, true, false);

        initService(false, true);
        service.setZeroValid(true);
        callbacks.clearEvents();

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(-100, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ПОЛЛЕНА ========================
    @Test
    public void testPollEnabled() throws Exception {
        // pollEnabled = true
        testScale.setCurrentWeight(500, true, false);
        initService(true, false);
        assertTrue(service.getPollEnabled());

        // Пропускаем POWER_ONLINE
        StatusUpdateEvent powerEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть событие POWER_ONLINE", powerEvent);

        // Ждем статусное событие
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3, TimeUnit.SECONDS);
        assertNotNull("Должно быть статусное событие при pollEnabled=true", event);

        service.close();
        Thread.sleep(1000);

        // pollEnabled = false
        service = new TestableScaleService();
        service.setTestScale(testScale);
        callbacks = new TestEventCallbacks();

        testScale.setCurrentWeight(500, true, false);

        initService(false, false);
        assertFalse(service.getPollEnabled());

        // Пропускаем POWER_ONLINE
        powerEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull(powerEvent);

        callbacks.clearEvents();
        Thread.sleep(1000);

        event = callbacks.waitForEvent(StatusUpdateEvent.class, 1, TimeUnit.SECONDS);
        assertNull("Не должно быть статусных событий при pollEnabled=false", event);

        cleanup();
    }
}
