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
import jpos.events.DataEvent;
import jpos.events.JposEvent;
import jpos.events.StatusUpdateEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.OutputCompleteEvent;
import jpos.services.EventCallbacks;

/**
 * Тест асинхронного режима ScaleService с использованием Mock ScaleSerial.
 */
@RunWith(MockitoJUnitRunner.class)
public class ScaleServiceAsyncTest {

    /**
     * Расширенный ScaleService для тестов, который возвращает мок ScaleSerial через createProtocol
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
        
        // Делаем все настройки lenient, чтобы избежать UnnecessaryStubbingException
        lenient().when(mockScaleSerial.getType()).thenReturn(EScale.Pos2);
        lenient().when(mockScaleSerial.getDeviceMetrics()).thenReturn(mock(DeviceMetrics.class));
        lenient().doNothing().when(mockScaleSerial).connect();
        lenient().doNothing().when(mockScaleSerial).disconnect();
        lenient().doNothing().when(mockScaleSerial).setParams(any(StringParams.class));
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

    @Test
    public void testAsyncReadWeightWithMultipleRequestsAndPollDisabled() throws Exception {
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
    public void testAsyncReadWeightWithUnstableWeightAndPollDisabled() throws Exception {
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
    public void testAsyncReadWeightWithTimeoutAndPollDisabled() throws Exception {
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
    public void testAsyncReadWeightWithZeroWeightAndZeroValidPollDisabled() throws Exception {
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
    public void testAsyncReadWeightWithZeroWeightAndZeroInvalidPollDisabled() throws Exception {
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

    @Test
    public void testAsyncReadWeightWithOverweightAndPollDisabled() throws Exception {
        when(mockScaleSerial.getWeight()).thenReturn(createOverweightWeight(2000, 0));

        initService(false);
        service.setAsyncMode(true);
        service.readWeight(null, 1000);

        Thread.sleep(500);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 100, TimeUnit.MILLISECONDS);
        assertNull("Не должно быть DataEvent при перегрузе", dataEvent);
        
        cleanup();
    }

    @Test
    public void testAsyncReadWeightWithAutoDisableAndPollDisabled() throws Exception {
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

    @Test
    public void testAsyncReadWeightWithTareAndPollDisabled() throws Exception {
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
    public void testPollEnabledProperty() throws Exception {
        service.open("TestScale", callbacks);
        
        assertTrue("По умолчанию pollEnabled должен быть true", service.getPollEnabled());
        
        service.setPollEnabled(false);
        assertFalse("pollEnabled должен быть false", service.getPollEnabled());
        
        service.setPollEnabled(true);
        assertTrue("pollEnabled должен быть true", service.getPollEnabled());
        
        service.close();
    }

    @Test
    public void testStatusUpdateEventsWithPollEnabled() throws Exception {
        when(mockScaleSerial.getWeight()).thenAnswer(new Answer<ScaleWeight>() {
            private int callCount = 0;
            
            @Override
            public ScaleWeight answer(InvocationOnMock invocation) throws Throwable {
                callCount++;
                if (callCount == 1) {
                    return createUnstableWeight(500, 0);
                } else {
                    return createStableWeight(500, 0);
                }
            }
        });

        initService(true); // pollEnabled = true
        
        StatusUpdateEvent statusEvent = callbacks.waitForEvent(StatusUpdateEvent.class, 2, TimeUnit.SECONDS);
        assertNotNull("Должно быть получено StatusUpdateEvent", statusEvent);
        
        cleanup();
    }

    // Вспомогательные методы для создания тестовых весов
    private ScaleWeight createStableWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x10);
        return new ScaleWeight(weight, tare, status);
    }

    private ScaleWeight createUnstableWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x00);
        return new ScaleWeight(weight, tare, status);
    }

    private ScaleWeight createOverweightWeight(long weight, long tare) {
        ScaleStatus status = new ScaleStatus(0x40);
        return new ScaleWeight(weight, tare, status);
    }
}