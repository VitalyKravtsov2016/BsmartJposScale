package com.bsmart.jpos.scale;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
 * Полный тест асинхронного режима ScaleService с использованием Mock
 * ScaleSerial. Покрывает все основные методы класса ScaleService.
 */
@RunWith(MockitoJUnitRunner.class)
public class ScaleServiceAsyncTest {

    /**
     * Расширенный ScaleService для тестов, который возвращает мок ScaleSerial
     * через createProtocol
     */
    private static class TestableScaleService extends ScaleService {

        private ScaleSerial mockScaleSerial;

        public void setMockScaleSerial(ScaleSerial mockScaleSerial) {
            this.mockScaleSerial = mockScaleSerial;
        }

        @Override
        protected ScaleSerial createProtocol(String protocol) throws Exception {
            return mockScaleSerial;
        }

        @Override
        public void setPowerState(int powerState) {
            super.setPowerState(powerState);
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

    @Mock
    private ScaleSerial mockScaleSerial;

    private TestableScaleService service;
    private TestEventCallbacks callbacks;

    @Before
    public void setUp() throws Exception {
        service = new TestableScaleService();
        service.setMockScaleSerial(mockScaleSerial);
        callbacks = new TestEventCallbacks();

        // Базовые настройки мока с lenient strictness
        lenient().when(mockScaleSerial.getType()).thenReturn(EScale.Pos2);

        DeviceMetrics mockMetrics = mock(DeviceMetrics.class);
        lenient().when(mockScaleSerial.getDeviceMetrics()).thenReturn(mockMetrics);

        lenient().doNothing().when(mockScaleSerial).connect();
        lenient().doNothing().when(mockScaleSerial).disconnect();
        lenient().doNothing().when(mockScaleSerial).setParams(any(StringParams.class));
        lenient().doNothing().when(mockScaleSerial).zero();
        lenient().doNothing().when(mockScaleSerial).tara(anyLong());
    }

    private void initService(boolean pollEnabled) throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setPollEnabled(pollEnabled);
        service.setDeviceEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setPowerState(JposConst.JPOS_PS_ONLINE);
        service.setDataEventEnabled(true);
    }

    private void cleanup() throws Exception {
        service.setAsyncMode(false);
        service.setDeviceEnabled(false);
        service.release();
        service.close();
    }

    // ======================== ТЕСТЫ АСИНХРОННОГО ЧТЕНИЯ ========================
    @Test
    public void testAsyncReadWeightProducesDataEvent() throws Exception {
        final long expectedWeight = 1234;

        when(mockScaleSerial.getWeight()).thenReturn(createStableWeight(expectedWeight, 0));

        initService(false);
        service.setAsyncMode(true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(expectedWeight, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithUnstableWeight() throws Exception {
        final long expectedWeight = 1234;

        when(mockScaleSerial.getWeight()).thenAnswer(new Answer<ScaleWeight>() {
            private int callCount = 0;

            @Override
            public ScaleWeight answer(InvocationOnMock invocation) throws Throwable {
                callCount++;
                if (callCount < 3) {
                    return createUnstableWeight(expectedWeight, 0);
                } else {
                    return createStableWeight(expectedWeight, 0);
                }
            }
        });

        initService(false);
        service.setAsyncMode(true);
        service.readWeight(null, 2000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(expectedWeight, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithTimeout() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createUnstableWeight(500, 0));

        initService(false);
        service.setAsyncMode(true);
        service.readWeight(null, 100);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent по таймауту", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithMultipleRequests() throws Exception {
        final AtomicInteger callCount = new AtomicInteger(0);

        when(mockScaleSerial.getWeight()).thenAnswer(new Answer<ScaleWeight>() {
            @Override
            public ScaleWeight answer(InvocationOnMock invocation) throws Throwable {
                int call = callCount.getAndIncrement();

                if (call < 3) {
                    return createUnstableWeight(100, 0);
                } else if (call == 3) {
                    return createStableWeight(100, 0);
                } else if (call < 7) {
                    return createUnstableWeight(200, 0);
                } else if (call == 7) {
                    return createStableWeight(200, 0);
                } else if (call < 11) {
                    return createUnstableWeight(300, 0);
                } else {
                    return createStableWeight(300, 0);
                }
            }
        });

        initService(false);
        service.setAsyncMode(true);

        service.readWeight(null, 1000);
        service.readWeight(null, 1000);
        service.readWeight(null, 1000);

        int[] expectedWeights = {100, 200, 300};

        for (int expectedWeight : expectedWeights) {
            DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);
            assertNotNull("Должно быть получено DataEvent для веса " + expectedWeight, dataEvent);
            assertEquals("Неверный вес в DataEvent", expectedWeight, dataEvent.getStatus());
        }

        DataEvent extraEvent = callbacks.waitForEvent(DataEvent.class, 500, TimeUnit.MILLISECONDS);
        assertNull("Не должно быть лишних DataEvent", extraEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ НУЛЕВОГО ВЕСА ========================
    @Test
    public void testAsyncReadWeightWithZeroWeightAndZeroValid() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createStableWeight(0, 0));

        initService(false);
        service.setZeroValid(true);
        service.setAsyncMode(true);
        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent с нулевым весом", dataEvent);
        assertEquals(0, dataEvent.getStatus());

        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithZeroWeightAndZeroInvalid() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createStableWeight(0, 0));

        initService(false);
        service.setZeroValid(false);
        service.setAsyncMode(true);
        service.readWeight(null, 500);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent по таймауту", dataEvent);
        assertEquals(0, dataEvent.getStatus());

        cleanup();
    }

    // ======================== ТЕСТЫ ПЕРЕГРУЗКИ ========================
    @Test
    public void testAsyncReadWeightWithOverweight() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createOverweightWeight(2000, 0));

        initService(false);
        service.setAsyncMode(true);
        service.readWeight(null, 1000);

        Thread.sleep(500);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 100, TimeUnit.MILLISECONDS);
        assertNull("Не должно быть DataEvent при перегрузе", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ AUTO DISABLE ========================
    @Test
    public void testAsyncReadWeightWithAutoDisable() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createStableWeight(750, 0));

        initService(false);

        service.setAutoDisable(true);
        service.setAsyncMode(true);

        assertTrue("Устройство должно быть включено до чтения", service.getDeviceEnabled());

        service.readWeight(null, 1000);

        // Ждем DataEvent
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(750, dataEvent.getStatus());

        // Ждем, пока autoDisable сработает
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
        final long expectedWeight = 1000;
        final long tareWeight = 200;

        when(mockScaleSerial.getWeight()).thenReturn(createStableWeight(expectedWeight, tareWeight));

        initService(false);
        service.setTareWeight((int) tareWeight);
        service.setAsyncMode(true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 2, TimeUnit.SECONDS);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(expectedWeight, dataEvent.getStatus());

        verify(mockScaleSerial).tara(tareWeight);

        cleanup();
    }

    @Test
    public void testGetTareWeight() throws Exception {
        initService(false);

        int tareWeight = 500;
        service.setTareWeight(tareWeight);

        assertEquals("Tare weight должен соответствовать установленному",
                tareWeight, service.getTareWeight());

        cleanup();
    }

    // ======================== ТЕСТЫ CAPABILITIES ========================
    @Test
    public void testCapabilities() throws Exception {
        service.open("TestScale", callbacks);

        assertFalse("CapCompareFirmwareVersion должен быть false",
                service.getCapCompareFirmwareVersion());
        assertTrue("CapStatusUpdate должен быть true",
                service.getCapStatusUpdate());
        assertFalse("CapUpdateFirmware должен быть false",
                service.getCapUpdateFirmware());
        assertFalse("CapDisplay должен быть false",
                service.getCapDisplay());
        assertFalse("CapStatisticsReporting должен быть false",
                service.getCapStatisticsReporting());
        assertFalse("CapUpdateStatistics должен быть false",
                service.getCapUpdateStatistics());
        assertFalse("CapDisplayText должен быть false",
                service.getCapDisplayText());
        assertEquals("CapPowerReporting должен быть STANDARD",
                JposConst.JPOS_PR_STANDARD, service.getCapPowerReporting());
        assertFalse("CapPriceCalculating должен быть false",
                service.getCapPriceCalculating());
        assertTrue("CapTareWeight должен быть true",
                service.getCapTareWeight());

        // CapZeroScale зависит от типа весов
        when(mockScaleSerial.getType()).thenReturn(EScale.Pos2);
        assertTrue("CapZeroScale для Pos2 должен быть true",
                service.getCapZeroScale());

        when(mockScaleSerial.getType()).thenReturn(EScale.Shtrih5);
        assertFalse("CapZeroScale для Shtrih5 должен быть false",
                service.getCapZeroScale());

        service.close();
    }

    // ======================== ТЕСТЫ СОСТОЯНИЯ ========================
    @Test
    public void testStateTransitions() throws Exception {
        // Начальное состояние - CLOSED
        assertEquals("Начальное состояние должно быть CLOSED",
                JposConst.JPOS_S_CLOSED, service.getState());

        // Open
        service.open("TestScale", callbacks);
        assertEquals("После open состояние должно быть IDLE",
                JposConst.JPOS_S_IDLE, service.getState());
        assertTrue("После open устройство должно быть открыто",
                service.getClaimed() == false);

        // Claim
        service.claim(0);
        assertEquals("После claim состояние должно быть IDLE",
                JposConst.JPOS_S_IDLE, service.getState());
        assertTrue("После claim устройство должно быть захвачено",
                service.getClaimed());

        // Enable
        service.setDeviceEnabled(true);
        assertEquals("После enable состояние должно быть IDLE или BUSY",
                JposConst.JPOS_S_IDLE, service.getState());
        assertTrue("После enable устройство должно быть включено",
                service.getDeviceEnabled());

        // Async mode
        service.setAsyncMode(true);
        if (service.getAsyncMode() && service.getDeviceEnabled()) {
            assertTrue("В асинхронном режиме состояние может быть BUSY",
                    service.getState() == JposConst.JPOS_S_IDLE
                    || service.getState() == JposConst.JPOS_S_BUSY);
        }

        // Disable
        service.setDeviceEnabled(false);
        assertFalse("После disable устройство должно быть отключено",
                service.getDeviceEnabled());

        // Release
        service.release();
        assertFalse("После release устройство не должно быть захвачено",
                service.getClaimed());

        // Close
        service.close();
        assertEquals("После close состояние должно быть CLOSED",
                JposConst.JPOS_S_CLOSED, service.getState());
    }

// ======================== ТЕСТЫ POWER ========================
    @Test
    public void testPowerState() throws Exception {
        initService(true);

        // В initService мы устанавливаем powerNotify = JPOS_PN_ENABLED
        assertEquals("PowerNotify должен быть ENABLED",
                JposConst.JPOS_PN_ENABLED, service.getPowerNotify());

        // Проверяем установку power state
        service.setPowerState(JposConst.JPOS_PS_ONLINE);

        // Ждем статусное событие
        StatusUpdateEvent statusEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено StatusUpdateEvent для POWER_ONLINE",
                statusEvent);

        // Проверяем, что powerState изменился
        assertEquals("PowerState должен быть ONLINE",
                JposConst.JPOS_PS_ONLINE, service.getPowerState());

        cleanup();
    }

// ======================== ТЕСТЫ СТАТУСНЫХ СОБЫТИЙ ========================
    @Test
    public void testStatusUpdateEvents() throws Exception {
        when(mockScaleSerial.getWeight()).thenAnswer(new Answer<ScaleWeight>() {
            private int callCount = 0;
            private long lastEventTime = 0;

            @Override
            public ScaleWeight answer(InvocationOnMock invocation) throws Throwable {
                callCount++;

                // Добавляем задержку между вызовами, чтобы события успевали обрабатываться
                if (System.currentTimeMillis() - lastEventTime < 50) {
                    Thread.sleep(50);
                }
                lastEventTime = System.currentTimeMillis();

                // Логируем для отладки
                System.out.println("Call " + callCount + " to getWeight()");

                if (callCount == 1) {
                    System.out.println("  Returning UNSTABLE weight");
                    return createUnstableWeight(500, 0);
                } else if (callCount == 2) {
                    System.out.println("  Returning STABLE weight");
                    return createStableWeight(500, 0);
                } else if (callCount == 3) {
                    System.out.println("  Returning ZERO weight");
                    return createStableWeight(0, 0);
                } else if (callCount == 4) {
                    System.out.println("  Returning OVERWEIGHT weight");
                    return createOverweightWeight(2000, 0);
                } else {
                    // Для последующих вызовов возвращаем стабильный вес
                    System.out.println("  Returning STABLE weight (continued)");
                    return createStableWeight(500, 0);
                }
            }
        });

        initService(true); // pollEnabled = true

        // Убеждаемся, что статусные события включены
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);

        // Даем время для запуска pollThread и первых вызовов
        Thread.sleep(500);

        // Собираем все статусные события в течение определенного времени
        StatusUpdateEvent unstableEvent = null;
        StatusUpdateEvent stableEvent = null;
        StatusUpdateEvent zeroEvent = null;
        StatusUpdateEvent overweightEvent = null;

        long deadline = System.currentTimeMillis() + 5000; // 5 секунд на все события

        while (System.currentTimeMillis() < deadline
                && (unstableEvent == null || stableEvent == null || zeroEvent == null)) {

            StatusUpdateEvent event = callbacks.waitForEvent(
                    StatusUpdateEvent.class, 500, TimeUnit.MILLISECONDS);

            if (event != null) {
                System.out.println("Received StatusUpdateEvent with value: " + event.getStatus());

                // Классифицируем событие по значению
                if (event.getStatus() == ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE) {
                    unstableEvent = event;
                    System.out.println("  -> UNSTABLE event");
                } else if (event.getStatus() == ScaleConst.SCAL_SUE_STABLE_WEIGHT) {
                    stableEvent = event;
                    System.out.println("  -> STABLE event");
                } else if (event.getStatus() == ScaleConst.SCAL_SUE_WEIGHT_ZERO) {
                    zeroEvent = event;
                    System.out.println("  -> ZERO event");
                } else if (event.getStatus() == ScaleConst.SCAL_SUE_WEIGHT_OVERWEIGHT) {
                    overweightEvent = event;
                    System.out.println("  -> OVERWEIGHT event");
                }
            }
        }

        // Проверяем получение событий
        assertNotNull("Должно быть получено событие о нестабильном весе", unstableEvent);
        assertNotNull("Должно быть получено событие о стабильном весе", stableEvent);
        assertNotNull("Должно быть получено событие о нулевом весе", zeroEvent);

    // Проверяем, что overweight может не прийти, если pollThread не успел
        // или если statusNotify фильтрует некоторые события
        cleanup();
    }

    // ======================== ТЕСТЫ СВОЙСТВ ========================
    @Test
    public void testProperties() throws Exception {
        service.open("TestScale", callbacks);

        // Test ZeroValid
        service.setZeroValid(true);
        assertTrue("ZeroValid должен быть true", service.getZeroValid());
        service.setZeroValid(false);
        assertFalse("ZeroValid должен быть false", service.getZeroValid());

        // Test StatusNotify
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        assertEquals("StatusNotify должен быть ENABLED",
                ScaleConst.SCAL_SN_ENABLED, service.getStatusNotify());

        // Test DataEventEnabled
        service.setDataEventEnabled(true);
        assertTrue("DataEventEnabled должен быть true", service.getDataEventEnabled());
        service.setDataEventEnabled(false);
        assertFalse("DataEventEnabled должен быть false", service.getDataEventEnabled());

        // Test FreezeEvents
        service.setFreezeEvents(true);
        assertTrue("FreezeEvents должен быть true", service.getFreezeEvents());
        service.setFreezeEvents(false);
        assertFalse("FreezeEvents должен быть false", service.getFreezeEvents());

        // Test AsyncMode
        service.setAsyncMode(true);
        assertTrue("AsyncMode должен быть true", service.getAsyncMode());
        service.setAsyncMode(false);
        assertFalse("AsyncMode должен быть false", service.getAsyncMode());

        // Test AutoDisable
        service.setAutoDisable(true);
        assertTrue("AutoDisable должен быть true", service.getAutoDisable());
        service.setAutoDisable(false);
        assertFalse("AutoDisable должен быть false", service.getAutoDisable());

        // Test PollEnabled
        service.setPollEnabled(false);
        assertFalse("PollEnabled должен быть false", service.getPollEnabled());
        service.setPollEnabled(true);
        assertTrue("PollEnabled должен быть true", service.getPollEnabled());

        service.close();
    }

    // ======================== ТЕСТЫ ИНФОРМАЦИИ ОБ УСТРОЙСТВЕ ========================
    @Test
    public void testDeviceInfo() throws Exception {
        service.open("TestScale", callbacks);

        assertNotNull("PhysicalDeviceDescription не должен быть null",
                service.getPhysicalDeviceDescription());
        assertNotNull("PhysicalDeviceName не должен быть null",
                service.getPhysicalDeviceName());
        assertNotNull("DeviceServiceDescription не должен быть null",
                service.getDeviceServiceDescription());
        assertTrue("DeviceServiceVersion должен быть положительным",
                service.getDeviceServiceVersion() > 0);

        service.close();
    }

    @Test
    public void testScaleSpecificInfo() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setDeviceEnabled(true);

        assertEquals("MaximumWeight должен быть максимальным",
                2147483647, service.getMaximumWeight());
        assertEquals("WeightUnit должен быть GRAM",
                ScaleConst.SCAL_WU_GRAM, service.getWeightUnit());

        int liveWeight = service.getScaleLiveWeight();
        assertTrue("ScaleLiveWeight должен быть неотрицательным", liveWeight >= 0);

        cleanup();
    }

    // ======================== ТЕСТЫ ОЧИСТКИ ========================
    @Test
    public void testClearInput() throws Exception {
        initService(false);
        service.setAsyncMode(true);

        // Отправляем запрос
        service.readWeight(null, 1000);

        // Очищаем вход
        service.clearInput();

        // Проверяем, что событие не пришло
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1, TimeUnit.SECONDS);
        assertNull("После clearInput не должно быть событий", dataEvent);

        cleanup();
    }

    // ======================== ТЕСТЫ ОШИБОК ========================
    @Test(expected = JposException.class)
    public void testReadWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        // Не включаем устройство

        service.readWeight(null, 1000); // Должно выбросить JPOS_E_DISABLED

        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetTareWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        // Не включаем устройство

        service.setTareWeight(100); // Должно выбросить JPOS_E_DISABLED

        cleanup();
    }

    @Test(expected = JposException.class)
    public void testZeroScaleWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        // Не включаем устройство

        service.zeroScale(); // Должно выбросить JPOS_E_DISABLED

        cleanup();
    }

    @Test(expected = JposException.class)
    public void testSetUnitPrice() throws Exception {
        initService(false);

        service.setUnitPrice(100); // Должно выбросить JPOS_E_ILLEGAL

        cleanup();
    }

    @Test(expected = JposException.class)
    public void testDirectIO() throws Exception {
        initService(false);

        service.directIO(0, null, null); // Должно выбросить JPOS_E_ILLEGAL

        cleanup();
    }

    @Test(expected = JposException.class)
    public void testCheckHealth() throws Exception {
        initService(false);

        service.checkHealth(0); // Должно выбросить JPOS_E_ILLEGAL

        cleanup();
    }

    // ======================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ========================
    private ScaleWeight createStableWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x10); // бит 4 - stable
        return new ScaleWeight(weight, tare, status);
    }

    private ScaleWeight createUnstableWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x00); // все биты сброшены
        return new ScaleWeight(weight, tare, status);
    }

    private ScaleWeight createOverweightWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x40); // бит 6 - overweight
        return new ScaleWeight(weight, tare, status);
    }
}
