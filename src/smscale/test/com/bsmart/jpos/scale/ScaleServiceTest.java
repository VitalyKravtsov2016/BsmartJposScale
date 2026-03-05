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
 * Исправленные тесты для ScaleService
 */
public class ScaleServiceTest {

    /**
     * Расширенный ScaleService для тестов
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
        DeviceMetrics metrics = new DeviceMetrics();
        //metrics.setMaximumWeight(30000); // 30kg
        //metrics.setMinimumWeight(20);    // 20g
        //metrics.setWeightUnit("g");
        testScale.setDeviceMetrics(metrics);
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
                Thread.sleep(100);
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
    public void testSyncReadWeightWithZeroValid() throws Exception {
        testScale.setCurrentWeight(0, true, false);

        initService(false, false);
        service.setZeroValid(true);

        int[] data = new int[1];
        service.readWeight(data, 2000);

        assertEquals("Нулевой вес должен быть принят", 0, data[0]);

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

        // Эти capability зависят от типа весов
        // В тесте они могут быть true или false
        // Просто проверяем, что метод работает без исключений
        service.getCapPriceCalculating();
        service.getCapTareWeight();

        // CapZeroScale зависит от типа весов
        service.getCapZeroScale();

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

        // Должно быть событие POWER_ONLINE при включении
        StatusUpdateEvent powerOnEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие POWER_ONLINE при включении", powerOnEvent);
        assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerOnEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ СТАТУСНЫХ СОБЫТИЙ ========================
    @Test
    public void testStatusUpdateEvents() throws Exception {
        initService(true, false);

        // Отключаем power notify, чтобы не мешало
        service.setDeviceEnabled(false);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);

        // Включаем статусные события
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        service.setDeviceEnabled(true);

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

        cleanup();
    }

    @Test
    public void testStatusNotifyDisabled() throws Exception {
        initService(true, false);

        // Отключаем все уведомления
        service.setDeviceEnabled(false);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);
        service.setStatusNotify(ScaleConst.SCAL_SN_DISABLED);
        service.setDeviceEnabled(true);

        callbacks.clearEvents();

        testScale.setCurrentWeight(500, false, false);

        // Ждем немного - событий быть не должно
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 500);
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
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 500);
        assertNull("События не должны приходить при freezeEvents=true", dataEvent);

        // Но должны накапливаться в очереди
        int dataCount = service.getDataCount();
        assertTrue("События должны накапливаться в очереди. Текущее значение: " + dataCount,
                dataCount > 0);

        service.setFreezeEvents(false);

        // После разморозки событие должно прийти
        dataEvent = callbacks.waitForEvent(DataEvent.class, 2000);
        assertNotNull("После разморозки событие должно прийти", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ОБРАБОТКИ ОШИБОК УСТРОЙСТВА ========================
    @Test
    public void testDeviceErrorNoLink() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Ошибка ERROR_NOLINK
        testScale.setNextException(new DeviceError(IDevice.ERROR_NOLINK, "No link to device"));
        service.readWeight(null, 0);

        // Должно быть ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        // PowerState должен измениться
        assertEquals(JposConst.JPOS_PS_OFF_OFFLINE, service.getPowerState());

        cleanup();
    }

    // ======================== ТЕСТЫ СВОЙСТВ ========================
    @Test
    public void testProperties() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);

        service.setZeroValid(true);
        assertTrue(service.getZeroValid());
        service.setZeroValid(false);
        assertFalse(service.getZeroValid());

        // StatusNotify можно менять только когда устройство выключено
        service.setDeviceEnabled(false);
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

    // ======================== ТЕСТЫ МЕТОДОВ СТАТИСТИКИ ========================
    @Test(expected = JposException.class)
    public void testResetStatisticsNotSupported() throws Exception {
        initService(false);
        service.resetStatistics("");
        cleanup();
    }

    // ======================== ТЕСТЫ КОНКУРЕНТНОГО ДОСТУПА ========================
    @Test
    public void testConcurrentReadWeight() throws Exception {
        // Устанавливаем вес и задержку ответа 2 секунды
        testScale.setCurrentWeight(500, true, false);
        testScale.setResponseDelay(100); // <-- Добавить эту строку

        initService(false, true);
        callbacks.clearEvents();

        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(5);

        Thread[] threads = new Thread[5];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger busyCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
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

        // Запускаем все одновременно
        startLatch.countDown();

        // Ждем завершения
        doneLatch.await(10000, TimeUnit.MILLISECONDS);

        // Должен быть только один успешный запрос
        assertEquals("Должен быть только один успешный запрос", 1, successCount.get());
        // Остальные должны получить BUSY
        assertEquals("Остальные должны получить BUSY", 4, busyCount.get());
        assertEquals("Не должно быть других ошибок", 0, otherErrorCount.get());

        // Должно быть только одно DataEvent
        DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull("Должно быть получено DataEvent", event);
        assertEquals(500, event.getStatus());

        cleanup();
    }
    
    // ======================== ТЕСТЫ РАЗНЫХ ПРОТОКОЛОВ ========================

    @Test
    public void testDifferentProtocols() throws Exception {
        testScale.setDeviceType(EScale.Pos2);
        service.open("TestScale", callbacks);
        String desc = service.getPhysicalDeviceDescription();
        assertNotNull("Description should not be null", desc);
        service.close();

        testScale.setDeviceType(EScale.Shtrih5);
        service.open("TestScale", callbacks);
        desc = service.getPhysicalDeviceDescription();
        assertNotNull("Description should not be null", desc);
        service.close();

        testScale.setDeviceType(EScale.Shtrih6);
        service.open("TestScale", callbacks);
        desc = service.getPhysicalDeviceDescription();
        assertNotNull("Description should not be null", desc);
        service.close();
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

        // Отрицательный вес должен вызывать ошибку
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 4000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);
        assertEquals(JposConst.JPOS_E_EXTENDED, errorEvent.getErrorCode());

        cleanup();
    }

    // ======================== ТЕСТЫ ПОЛЛЕНА ========================
    @Test
    public void testPollEnabled() throws Exception {
        // Просто проверяем, что метод работает
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        assertTrue(service.getPollEnabled());
        service.setPollEnabled(false);
        assertFalse(service.getPollEnabled());
        service.close();
    }
}
