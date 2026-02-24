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
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(pollEnabled);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(asyncMode);

        // Пропускаем событие POWER_ONLINE при включении
        if (pollEnabled) {
            StatusUpdateEvent powerEvent = callbacks.waitForEvent(
                    StatusUpdateEvent.class, 2000);
            if (powerEvent != null) {
                assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerEvent.getStatus());
            }
        }
    }

    private void initService(boolean pollEnabled) throws Exception {
        initService(pollEnabled, false);
    }

    private void cleanup() throws Exception {
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                // Игнорируем
            }
        }
    }

    // ======================== ТЕСТЫ АСИНХРОННОГО ЧТЕНИЯ ========================
    @Test
    public void testAsyncReadWeightProducesDataEvent() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);

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
                Thread.sleep(200);
                testScale.setCurrentWeight(1234, true, false);
            } catch (InterruptedException e) {
            }
        }).start();

        service.readWeight(null, 3000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 4000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1234, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithTimeout() throws Exception {
        testScale.setCurrentWeight(500, false, false); // Всегда нестабильный

        initService(false, true);
        service.readWeight(null, 100);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);

        assertNotNull("Должно быть получено DataEvent по таймауту", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithMultipleSequentialRequests() throws Exception {
        initService(false, true);

        // Первый запрос
        testScale.setCurrentWeight(100, true, false);
        service.readWeight(null, 2000);
        DataEvent event1 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent для первого запроса", event1);
        assertEquals(100, event1.getStatus());

        // Ждем завершения обработки
        Thread.sleep(500);

        // Второй запрос (после завершения первого)
        testScale.setCurrentWeight(200, true, false);
        service.readWeight(null, 2000);
        DataEvent event2 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent для второго запроса", event2);
        assertEquals(200, event2.getStatus());

        // Ждем завершения обработки
        Thread.sleep(500);

        // Третий запрос (после завершения второго)
        testScale.setCurrentWeight(300, true, false);
        service.readWeight(null, 2000);
        DataEvent event3 = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent для третьего запроса", event3);
        assertEquals(300, event3.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithConcurrentRequests() throws Exception {
        testScale.setCurrentWeight(500, true, false);
        initService(false, true);
        callbacks.clearEvents();

        // Первый запрос должен пройти
        service.readWeight(null, 5000);

        // Второй запрос должен выбросить JPOS_E_BUSY
        try {
            service.readWeight(null, 5000);
            fail("Должно быть выброшено JposException с кодом JPOS_E_BUSY");
        } catch (JposException e) {
            assertEquals("Код ошибки должен быть JPOS_E_BUSY",
                    JposConst.JPOS_E_BUSY, e.getErrorCode());
        }

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithInterruptedThread() throws Exception {
        testScale.setResponseDelay(2000); // Долгий ответ

        initService(false, true);
        callbacks.clearEvents();

        service.readWeight(null, 5000);
        Thread.sleep(100); // Даем время на запуск операции
        service.setAsyncMode(false); // Прерываем поток

        // Ждем завершения потока
        Thread.sleep(1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
        assertNull("Не должно быть DataEvent после прерывания", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ СИНХРОННОГО ЧТЕНИЯ ========================
    @Test
    public void testSyncReadWeight() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, false);

        int[] data = new int[1];
        service.readWeight(data, 2000);

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
        service.readWeight(data, 2000);

        assertEquals("Нулевой вес должен быть принят", 0, data[0]);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithZeroInvalid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, false);
        service.setZeroValid(false);

        int[] data = new int[1];
        long startTime = System.currentTimeMillis();
        service.readWeight(data, 500);
        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals("По таймауту вернется текущий вес", 0, data[0]);
        assertTrue("Должен ждать до таймаута", elapsed >= 450 && elapsed <= 600);

        cleanup();
    }

    @Test
    public void testSyncReadWeightWithOverweight() throws Exception {
        initService(false, false);

        // Сначала нормальный вес
        testScale.setCurrentWeight(500, true, false);
        int[] data = new int[1];
        service.readWeight(data, 2000);
        assertEquals(500, data[0]);

        // Перегруз - должно быть исключение
        testScale.setCurrentWeight(2000, true, true);

        try {
            service.readWeight(data, 2000);
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
        service.readWeight(null, 2000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);

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

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2000);

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
        service.readWeight(null, 2000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть DataEvent для нормального веса", normalEvent);
        assertEquals(500, normalEvent.getStatus());

        callbacks.clearEvents();

        // Перегруз
        testScale.setCurrentWeight(2000, true, true);
        service.readWeight(null, 2000);

        // В асинхронном режиме должно прийти ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть ErrorEvent при перегрузе", errorEvent);
        assertEquals("ErrorLocus должен быть EL_INPUT",
                JposConst.JPOS_EL_INPUT, errorEvent.getErrorLocus());
        assertEquals("ErrorResponse по умолчанию должен быть ER_CLEAR",
                JposConst.JPOS_ER_CLEAR, errorEvent.getErrorResponse());

        // Проверяем, что устройство перешло в состояние ошибки
        assertEquals("Устройство должно быть в состоянии ошибки",
                JposConst.JPOS_S_ERROR, service.getState());

        // DataEvent не должно быть
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
        assertNull("Не должно быть DataEvent при перегрузе", dataEvent);

        // Очищаем ошибку
        service.clearInput();
        assertEquals("После clearInput состояние должно вернуться к IDLE",
                JposConst.JPOS_S_IDLE, service.getState());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithOverweightAndRetry() throws Exception {
        initService(false, true);

        // Перегруз
        testScale.setCurrentWeight(2000, true, true);
        service.readWeight(null, 5000);

        // Ждем ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForErrorEvent(5000);
        assertNotNull("Должно быть ErrorEvent при перегрузе", errorEvent);

        // Меняем ответ на RETRY
        callbacks.setErrorResponse(JposConst.JPOS_ER_RETRY);

        // Убираем перегруз
        testScale.setCurrentWeight(500, true, false);

        // Должен автоматически повториться и прислать DataEvent
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull("После RETRY должно быть DataEvent", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ AUTO DISABLE ========================
    @Test
    public void testAsyncReadWeightWithAutoDisable() throws Exception {
        initService(false, true);
        service.setAutoDisable(true);

        assertTrue("Устройство должно быть включено до чтения", service.getDeviceEnabled());

        testScale.setCurrentWeight(750, true, false);
        service.readWeight(null, 2000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(750, dataEvent.getStatus());

        // Проверка autoDisable - должно отключиться синхронно после события
        Thread.sleep(500); // Даем время на обработку
        assertFalse("Устройство должно быть отключено после autoDisable", service.getDeviceEnabled());

        cleanup();
    }

    // ======================== ТЕСТЫ ТАРЫ ========================
    @Test
    public void testAsyncReadWeightWithTare() throws Exception {
        testScale.setCurrentWeight(1000, true, false);

        initService(false, true);
        service.setTareWeight(200);
        service.readWeight(null, 2000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);

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

    @Test
    public void testVersionCapabilities() throws Exception {
        service.open("TestScale", callbacks);

        // Проверяем capability для версии 1.14 (все false)
        assertFalse(service.getCapFreezeValue());
        assertFalse(service.getCapReadLiveWeightWithTare());
        assertFalse(service.getCapSetPriceCalculationMode());
        assertFalse(service.getCapSetUnitPriceWithWeightUnit());
        assertFalse(service.getCapSpecialTare());
        assertFalse(service.getCapTarePriority());
        assertEquals(0, service.getMinimumWeight());

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

        // Async mode - должно быть IDLE пока нет активной операции
        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());

        // Запускаем асинхронную операцию - должно стать BUSY
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 5000);

        // Даем время на установку флага
        Thread.sleep(100);
        assertEquals(JposConst.JPOS_S_BUSY, service.getState());

        // После получения данных должно вернуться в IDLE
        DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull(event);

        // Даем время на обработку
        Thread.sleep(500);
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());

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
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(false);

        // Пропускаем событие POWER_ONLINE от включения
        StatusUpdateEvent powerOnEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие POWER_ONLINE при включении", powerOnEvent);
        assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerOnEvent.getStatus());

        callbacks.clearEvents();

        // Устанавливаем OFFLINE
        service.setPowerState(JposConst.JPOS_PS_OFFLINE);
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
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
                StatusUpdateEvent.class, 1000);
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

        StatusUpdateEvent unstableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие о нестабильном весе", unstableEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE, unstableEvent.getStatus());

        callbacks.clearEvents();

        // Стабильный вес
        testScale.setCurrentWeight(500, true, false);

        StatusUpdateEvent stableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие о стабильном весе", stableEvent);
        assertEquals(ScaleConst.SCAL_SUE_STABLE_WEIGHT, stableEvent.getStatus());

        callbacks.clearEvents();

        // Нулевой вес
        testScale.setCurrentWeight(0, true, false);

        StatusUpdateEvent zeroEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
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

        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 1000);
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
        service.readWeight(null, 5000);

        // При freezeEvents=true события не должны доставляться
        // Но даем время на возможную доставку
        Thread.sleep(1000);
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
        assertNull("События не должны приходить при freezeEvents=true", dataEvent);

        // Но должны накапливаться в очереди
        int dataCount = service.getDataCount();
        assertTrue("События должны накапливаться в очереди. Текущее значение: " + dataCount,
                dataCount > 0);

        service.setFreezeEvents(false);

        // После разморозки событие должно прийти
        dataEvent = callbacks.waitForEvent(DataEvent.class, 5000);
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
        service.readWeight(null, 2000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull(normalEvent);
        assertEquals(500, normalEvent.getStatus());

        callbacks.clearEvents();

        // Ошибка ERROR_NOLINK
        testScale.setNextException(new DeviceError(IDevice.ERROR_NOLINK, "No link to device"));
        service.readWeight(null, 0);

        // Должно быть ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        // PowerState должен измениться
        assertEquals(JposConst.JPOS_PS_OFF_OFFLINE, service.getPowerState());

        // Устройство должно перейти в состояние ошибки
        assertEquals(JposConst.JPOS_S_ERROR, service.getState());

        cleanup();
    }

    @Test
    public void testDeviceErrorGeneric() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Нормальная работа
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 2000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull(normalEvent);

        callbacks.clearEvents();

        // Общая ошибка
        testScale.setNextException(new RuntimeException("Generic error"));
        service.readWeight(null, 2000);

        assertEquals(JposConst.JPOS_PS_ONLINE, service.getPowerState());

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        // Устройство должно перейти в состояние ошибки
        assertEquals(JposConst.JPOS_S_ERROR, service.getState());

        cleanup();
    }

    @Test
    public void testDeviceErrorOther() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Нормальная работа
        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 2000);

        DataEvent normalEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull(normalEvent);

        callbacks.clearEvents();

        // Другая ошибка
        testScale.setNextException(new DeviceError(999, "Other error"));
        service.readWeight(null, 2000);

        assertEquals(JposConst.JPOS_PS_ONLINE, service.getPowerState());

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        // Устройство должно перейти в состояние ошибки
        assertEquals(JposConst.JPOS_S_ERROR, service.getState());

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
        service.readWeight(null, 2000);

        // Ждем DataEvent
        DataEvent event = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull(event);

        service.clearInput();

        // Проверяем, что счетчик событий сброшен
        assertEquals(0, service.getDataCount());

        cleanup();
    }

    @Test
    public void testClearInputWithPendingRequests() throws Exception {
        testScale.setResponseDelay(2000);
        initService(false, true);

        // Отправляем запрос
        service.readWeight(null, 5000);

        // Ждем немного, чтобы запрос начал обрабатываться
        Thread.sleep(500);

        // Сохраняем количество событий до clearInput
        int beforeClear = service.getDataCount();

        service.clearInput();

        // Проверяем, что очередь событий очищена
        assertEquals(0, service.getDataCount());

        callbacks.clearEvents();

        // Ждем - событий быть не должно
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2000);
        assertNull("Не должно быть событий после clearInput", dataEvent);

        cleanup();
    }

    @Test
    public void testClearInputWithErrorState() throws Exception {
        initService(false, true);

        // Вызываем ошибку
        testScale.setCurrentWeight(2000, true, true);
        service.readWeight(null, 2000);

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull(errorEvent);

        // Проверяем состояние ошибки
        assertEquals(JposConst.JPOS_S_ERROR, service.getState());

        // Очищаем ошибку
        service.clearInput();

        // Проверяем, что состояние восстановилось
        Thread.sleep(500);
        assertEquals(JposConst.JPOS_S_IDLE, service.getState());

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

    // ======================== ТЕСТЫ МЕТОДОВ СТАТИСТИКИ ========================
    @Test(expected = JposException.class)
    public void testResetStatisticsNotSupported() throws Exception {
        initService(false);
        service.resetStatistics("");
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testRetrieveStatisticsNotSupported() throws Exception {
        initService(false);
        service.retrieveStatistics(new String[1]);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testUpdateStatisticsNotSupported() throws Exception {
        initService(false);
        service.updateStatistics("");
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testCompareFirmwareVersionNotSupported() throws Exception {
        initService(false);
        service.compareFirmwareVersion("", new int[1]);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testUpdateFirmwareNotSupported() throws Exception {
        initService(false);
        service.updateFirmware("");
        cleanup();
    }

    // ======================== ТЕСТЫ КОНКУРЕНТНОГО ДОСТУПА ========================
    @Test
    public void testConcurrentReadWeight() throws Exception {
        testScale.setCurrentWeight(500, true, false);
        initService(false, true);
        callbacks.clearEvents();

        // Используем CountDownLatch для синхронизации запуска потоков
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(5);

        Thread[] threads = new Thread[5];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger busyCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await(); // Ждем сигнала на старт
                    service.readWeight(null, 5000);
                    successCount.incrementAndGet();
                } catch (JposException e) {
                    if (e.getErrorCode() == JposConst.JPOS_E_BUSY) {
                        busyCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }

        // Запускаем все потоки одновременно
        startLatch.countDown();

        // Ждем завершения всех потоков
        doneLatch.await(10000, TimeUnit.MILLISECONDS);

        // Должен быть только один успешный запрос
        assertEquals("Должен быть только один успешный запрос", 1, successCount.get());
        // Остальные должны получить BUSY
        assertEquals("Остальные должны получить BUSY", 4, busyCount.get());
        assertEquals("Не должно быть других ошибок", 0, otherErrorCount.get());

        // Должно быть только одно DataEvent
        DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull("Должно быть получено DataEvent", event);

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
                e.printStackTrace();
            }
        });

        Thread setter2 = new Thread(() -> {
            try {
                service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
                service.setPollEnabled(false);
            } catch (JposException e) {
                errorCount.incrementAndGet();
                e.printStackTrace();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                service.readWeight(null, 3000);
            } catch (JposException e) {
                // Может быть BUSY, но это не ошибка теста
                if (e.getErrorCode() != JposConst.JPOS_E_BUSY) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                }
            }
        });

        setter1.start();
        setter2.start();
        Thread.sleep(100);
        reader.start();

        setter1.join(2000);
        setter2.join(2000);
        reader.join(4000);

        assertEquals(0, errorCount.get());

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

        // Регистрируем только первый callbacks
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(true);
        service.setDataEventEnabled(true);

        service.readWeight(null, 5000);

        // Первый слушатель должен получить событие
        DataEvent event1 = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull("Первый слушатель должен получить событие", event1);

        // Второй слушатель не должен получать события, так как он не зарегистрирован
        DataEvent event2 = secondCallbacks.waitForEvent(DataEvent.class, 2000);
        assertNull("Второй слушатель не должен получить событие", event2);

        cleanup();
    }

    // ======================== ТЕСТЫ ГРАНИЧНЫХ ЗНАЧЕНИЙ ========================
    @Test
    public void testExtremeWeightValues() throws Exception {
        testScale.setCurrentWeight(Integer.MAX_VALUE, true, false);

        initService(false, true);
        service.readWeight(null, 3000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 4000);
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

        service.readWeight(null, 3000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 4000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(-100, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ПОЛЛЕНА ========================
    @Test
    public void testPollEnabled() throws Exception {
        // pollEnabled = true
        testScale.setCurrentWeight(500, true, false);
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(false);
        assertTrue(service.getPollEnabled());

        // Пропускаем POWER_ONLINE
        StatusUpdateEvent powerEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие POWER_ONLINE", powerEvent);

        // Ждем статусное событие от поллинга
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 4000);
        assertNotNull("Должно быть статусное событие при pollEnabled=true", event);

        service.close();

        // pollEnabled = false
        service = new TestableScaleService();
        service.setTestScale(testScale);
        callbacks = new TestEventCallbacks();

        testScale.setCurrentWeight(500, true, false);

        initService(false, false);
        assertFalse(service.getPollEnabled());

        // Пропускаем POWER_ONLINE
        powerEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull(powerEvent);

        callbacks.clearEvents();

        // Не должно быть статусных событий от поллинга
        event = callbacks.waitForEvent(StatusUpdateEvent.class, 2000);
        assertNull("Не должно быть статусных событий при pollEnabled=false", event);

        cleanup();
    }

    // ======================== ТЕСТЫ МЕТОДОВ ВЕРСИИ 1.14 ========================
    @Test(expected = JposException.class)
    public void testDoPriceCalculatingNotSupported() throws Exception {
        initService(false);
        service.doPriceCalculating(new int[1], new int[1], new long[1],
                new long[1], new int[1], new int[1], new int[1], new long[1], 1000);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testFreezeValueNotSupported() throws Exception {
        initService(false);
        service.freezeValue(0, true);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testReadLiveWeightWithTareNotSupported() throws Exception {
        initService(false);
        service.readLiveWeightWithTare(new int[1], new int[1], 1000);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetPriceCalculationModeNotSupported() throws Exception {
        initService(false);
        service.setPriceCalculationMode(0);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetSpecialTareNotSupported() throws Exception {
        initService(false);
        service.setSpecialTare(0, 0);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetTarePriorityNotSupported() throws Exception {
        initService(false);
        service.setTarePriority(0);
        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetUnitPriceWithWeightUnitNotSupported() throws Exception {
        initService(false);
        service.setUnitPriceWithWeightUnit(100, ScaleConst.SCAL_WU_GRAM, 1, 1);
        cleanup();
    }
}
