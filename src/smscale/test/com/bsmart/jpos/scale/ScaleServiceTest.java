package com.bsmart.jpos.scale;

import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.bsmart.IDevice;
import com.bsmart.DeviceError;
import com.bsmart.jpos.scale.TestScaleSerial;
import com.bsmart.scale.EScale;
import com.bsmart.scale.ScaleSerial;
import com.bsmart.scale.DeviceMetrics;

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
        
        // Метод для прямого доступа к состоянию из тестов
        public int getCurrentState() {
            try {
                return getState();
            } catch (JposException e) {
                return -1;
            }
        }
    }

    /**
     * Расширенный EventCallbacks для тестирования вызовов из обработчика
     */
    private static class TestEventCallbacksWithHandler implements EventCallbacks {
        
        private final BlockingQueue<JposEvent> events = new LinkedBlockingQueue<>();
        
        private volatile Runnable actionToPerform;
        
        public void setAction(Runnable action) {
            this.actionToPerform = action;
        }
        
        public void clearEvents() {
            events.clear();
        }

        public <T extends JposEvent> T waitForEvent(Class<T> type, long timeout) {
            try {
                JposEvent event = events.poll(timeout, TimeUnit.MILLISECONDS);
                if (type.isInstance(event)) {
                    return type.cast(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public void fireDataEvent(DataEvent event) {
            // Выполняем действие приложения (если есть)
            if (actionToPerform != null) {
                actionToPerform.run();
            }
            
            // Отправляем событие
            events.offer(event);
        }

        @Override
        public void fireDirectIOEvent(DirectIOEvent event) {
            events.offer(event);
        }

        @Override
        public void fireErrorEvent(ErrorEvent event) {
            events.offer(event);
        }

        @Override
        public void fireOutputCompleteEvent(OutputCompleteEvent event) {
            events.offer(event);
        }

        @Override
        public void fireStatusUpdateEvent(StatusUpdateEvent event) {
            events.offer(event);
        }

        @Override
        public BaseControl getEventSource() {
            return null;
        }
    }

    private TestScaleSerial testScale;
    private TestableScaleService service;
    private TestEventCallbacksWithHandler callbacks;

    @Before
    public void setUp() throws Exception {
        testScale = new TestScaleSerial();
        service = new TestableScaleService();
        service.setTestScale(testScale);
        callbacks = new TestEventCallbacksWithHandler();

        // Базовая настройка тестового устройства
        testScale.setDeviceType(EScale.Pos2);
        DeviceMetrics metrics = new DeviceMetrics();
        testScale.setDeviceMetrics(metrics);
    }

    private void initService(boolean pollEnabled, boolean asyncMode) throws Exception {
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(pollEnabled);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(asyncMode);

        // Пропускаем возможные события при включении
        Thread.sleep(100);
        callbacks.clearEvents();
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

    // ======================== ТЕСТЫ НЕДОПУСТИМЫХ ВЫЗОВОВ ========================

    /**
     * Тест: вызов close() в обработчике DataEvent должен выбрасывать JPOS_E_ILLEGAL
     * (не JPOS_E_BUSY, так как это защита от deadlock, а не состояние устройства)
     */
    @Test
    public void testCloseInDataEventHandlerThrowsIllegal() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.close();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        // Проверяем исключение из обработчика
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        
        // Должен быть JPOS_E_ILLEGAL, не JPOS_E_BUSY
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        // Сообщение должно указывать на вызов из обработчика событий
        assertTrue("Сообщение должно указывать на вызов из обработчика событий",
                   exceptionFromHandler[0].getMessage() != null &&
                   exceptionFromHandler[0].getMessage().contains("event handler"));
        
        // Проверяем состояние ПОСЛЕ доставки события - устройство должно быть в IDLE
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        // Устройство должно остаться в рабочем состоянии
        assertTrue("Устройство должно остаться заявленным", service.getClaimed());
        assertTrue("Устройство должно остаться включенным", service.getDeviceEnabled());
        
        cleanup();
    }

    /**
     * Тест: вызов release() в обработчике DataEvent должен выбрасывать JPOS_E_ILLEGAL
     */
    @Test
    public void testReleaseInDataEventHandlerThrowsIllegal() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.release();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        assertTrue("Сообщение должно указывать на вызов из обработчика событий",
                   exceptionFromHandler[0].getMessage().contains("event handler"));
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        assertTrue("Устройство должно остаться заявленным", service.getClaimed());
        
        cleanup();
    }

    /**
     * Тест: вызов setDeviceEnabled(false) в обработчике DataEvent должен выбрасывать JPOS_E_ILLEGAL
     */
    @Test
    public void testSetDeviceEnabledInDataEventHandlerThrowsIllegal() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.setDeviceEnabled(false);
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        assertTrue("Сообщение должно указывать на вызов из обработчика событий",
                   exceptionFromHandler[0].getMessage().contains("event handler"));
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        assertTrue("Устройство должно остаться включенным", service.getDeviceEnabled());
        
        cleanup();
    }

    /**
     * Тест: вызов readWeight() в обработчике DataEvent должен выбрасывать JPOS_E_ILLEGAL
     * (это остается BUSY, потому что устройство уже выполняет асинхронную операцию)
     */
    @Test
    public void testReadWeightInDataEventHandlerThrowsBusy() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.readWeight(null, 1000);
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        
        // readWeight проверяет checkNotInEventThread()
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        cleanup();
    }

    /**
     * Тест: вызов zeroScale() в обработчике DataEvent должен выбрасывать JPOS_E_BUSY
     * (zeroScale тоже проверяет S_BUSY через checkIdleState())
     */
    @Test
    public void testZeroScaleInDataEventHandlerThrowsIllegal() throws Exception {
        initService(false, true);
        
        // Проверяем, поддерживается ли метод
        if (!service.getCapZeroScale()) {
            return; // Пропускаем тест
        }
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.zeroScale();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        
        // zeroScale нельзя вызывать из обработчика
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        cleanup();
    }

    // ======================== ТЕСТЫ РАЗРЕШЕННЫХ ВЫЗОВОВ ========================

    /**
     * Тест: вызов clearInput() разрешен в обработчике DataEvent
     */
    @Test
    public void testClearInputAllowedInDataEventHandler() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.clearInput();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNull("Не должно быть исключения из обработчика", exceptionFromHandler[0]);
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        cleanup();
    }

    /**
     * Тест: чтение свойств разрешено в обработчике DataEvent
     */
    @Test
    public void testGetPropertiesAllowedInDataEventHandler() throws Exception {
        initService(false, true);
        
        // Устанавливаем тару
        service.setTareWeight(200);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        final Object[] properties = new Object[5];
        
        callbacks.setAction(() -> {
            try {
                properties[0] = service.getScaleLiveWeight();
                properties[1] = service.getSalesPrice();
                properties[2] = service.getTareWeight();
                properties[3] = service.getMaximumWeight();
                properties[4] = service.getWeightUnit();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNull("Не должно быть исключения из обработчика", exceptionFromHandler[0]);
        
        // Проверяем, что свойства прочитаны
        assertNotNull("Свойства должны быть прочитаны", properties[0]);
        assertNotNull("Свойства должны быть прочитаны", properties[1]);
        assertNotNull("Свойства должны быть прочитаны", properties[2]);
        assertNotNull("Свойства должны быть прочитаны", properties[3]);
        assertNotNull("Свойства должны быть прочитаны", properties[4]);
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        cleanup();
    }

    // ======================== ТЕСТЫ АСИНХРОННОГО ЧТЕНИЯ ========================

    /**
     * Тест: асинхронное чтение веса должно генерировать DataEvent
     */
    @Test
    public void testAsyncReadWeightProducesDataEvent() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1234, dataEvent.getStatus());
        
        // Проверяем состояние ПОСЛЕ доставки события
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());

        cleanup();
    }

    // ======================== ОСТАЛЬНЫЕ ТЕСТЫ ========================

    /**
     * Тест: проверка capability свойств
     */
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
        service.getCapPriceCalculating();
        service.getCapTareWeight();
        service.getCapZeroScale();

        service.close();
    }

    /**
     * Тест: проверка переходов состояний устройства
     */
    @Test
    public void testStateTransitions() throws Exception {
        // Начальное состояние - CLOSED
        assertEquals(JposConst.JPOS_S_CLOSED, service.getCurrentState());

        // Open
        service.open("TestScale", callbacks);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertFalse(service.getClaimed());

        // Claim
        service.claim(0);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertTrue(service.getClaimed());

        // Enable
        service.setDeviceEnabled(true);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertTrue(service.getDeviceEnabled());

        // Async mode - должно быть IDLE пока нет активной операции
        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());

        // Disable
        service.setDeviceEnabled(false);
        assertFalse(service.getDeviceEnabled());
        assertFalse(service.getAsyncMode());

        // Release
        service.release();
        assertFalse(service.getClaimed());

        // Close
        service.close();
        assertEquals(JposConst.JPOS_S_CLOSED, service.getCurrentState());
    }

    /**
     * Тест: уведомления о состоянии питания
     */
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

    /**
     * Тест: статусные события весов
     */
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

    /**
     * Тест: отключение статусных уведомлений
     */
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

    /**
     * Тест: заморозка событий
     */
    @Test
    public void testFreezeEvents() throws Exception {
        initService(false, true);

        service.setFreezeEvents(true);
        service.setDataEventEnabled(true);
        callbacks.clearEvents();

        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 5000);

        // При freezeEvents=true события не должны доставляться
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
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

    /**
     * Тест: ошибка устройства ERROR_NOLINK
     */
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

        cleanup();
    }

    /**
     * Тест: установка и чтение свойств
     */
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

    /**
     * Тест: информация об устройстве
     */
    @Test
    public void testDeviceInfo() throws Exception {
        service.open("TestScale", callbacks);

        assertNotNull(service.getPhysicalDeviceDescription());
        assertNotNull(service.getPhysicalDeviceName());
        assertNotNull(service.getDeviceServiceDescription());
        assertTrue(service.getDeviceServiceVersion() > 0);

        service.close();
    }

    /**
     * Тест: очистка входного буфера
     */
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

    /**
     * Тест: readWeight когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testReadWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.readWeight(null, 1000);
        cleanup();
    }

    /**
     * Тест: setTareWeight когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testSetTareWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setTareWeight(100);
        cleanup();
    }

    /**
     * Тест: zeroScale когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testZeroScaleWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.zeroScale();
        cleanup();
    }

    /**
     * Тест: setUnitPrice (не поддерживается)
     */
    @Test(expected = JposException.class)
    public void testSetUnitPrice() throws Exception {
        initService(false);
        service.setUnitPrice(100);
        cleanup();
    }

    /**
     * Тест: resetStatistics (не поддерживается)
     */
    @Test(expected = JposException.class)
    public void testResetStatisticsNotSupported() throws Exception {
        initService(false);
        service.resetStatistics("");
        cleanup();
    }

    /**
     * Тест: конкурентный доступ к readWeight
     */
    @Test
    public void testConcurrentReadWeight() throws Exception {
        // Устанавливаем вес
        testScale.setCurrentWeight(500, true, false);
        testScale.setResponseDelay(100);

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
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }

        startLatch.countDown();
        doneLatch.await(1000, TimeUnit.MILLISECONDS);

        assertEquals(1, successCount.get());
        assertEquals(4, busyCount.get());
        assertEquals(0, otherErrorCount.get());

        DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull(event);
        assertEquals(500, event.getStatus());

        cleanup();
    }

    /**
     * Тест: разные протоколы весов
     */
    @Test
    public void testDifferentProtocols() throws Exception {
        testScale.setDeviceType(EScale.Pos2);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();

        testScale.setDeviceType(EScale.Shtrih5);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();

        testScale.setDeviceType(EScale.Shtrih6);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();
    }

    /**
     * Тест: максимальное значение веса
     */
    @Test
    public void testExtremeWeightValues() throws Exception {
        testScale.setCurrentWeight(Integer.MAX_VALUE, true, false);

        initService(false, true);
        service.readWeight(null, 3000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 4000);
        assertNotNull(dataEvent);
        assertEquals(Integer.MAX_VALUE, dataEvent.getStatus());

        cleanup();
    }

    /**
     * Тест: отрицательный вес
     */
    @Test
    public void testNegativeWeight() throws Exception {
        testScale.setCurrentWeight(-100, true, false);

        initService(false, true);
        service.setZeroValid(true);
        callbacks.clearEvents();

        service.readWeight(null, 3000);

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 4000);
        assertNotNull(errorEvent);
        assertEquals(JposConst.JPOS_E_EXTENDED, errorEvent.getErrorCode());

        cleanup();
    }

    /**
     * Тест: включение/отключение poll
     */
    @Test
    public void testPollEnabled() throws Exception {
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        assertTrue(service.getPollEnabled());
        service.setPollEnabled(false);
        assertFalse(service.getPollEnabled());
        service.close();
    }
}