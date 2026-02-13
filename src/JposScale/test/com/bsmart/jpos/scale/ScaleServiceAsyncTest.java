package com.bsmart.jpos.scale;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import jpos.JposException;
import jpos.events.DataEvent;

/**
 * Тест асинхронного режима ScaleService: проверяет, что при обработке
 * WeightRequest создаётся DataEvent с ожидаемым весом в status.
 */
public class ScaleServiceAsyncTest {

    /**
     * Специализированный сервис для теста, который не обращается к реальным весам,
     * а возвращает фиксированный вес из readWeightTimeout.
     */
    private static class TestScaleService extends ScaleService {
        private long testWeight;

        public void setTestWeight(long weight) {
            this.testWeight = weight;
        }

        @Override
        public long readWeightTimeout(int timeout) throws JposException, InterruptedException {
            return testWeight;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAsyncWeightProcProducesDataEventWithWeightInStatus() throws Exception {
        final long expectedWeight = 1234;

        TestScaleService service = new TestScaleService();
        service.setTestWeight(expectedWeight);
        // включим генерацию событий данных (флаг влияет на eventProc, но не мешает weightProc)
        service.setDataEventEnabled(true);

        // Достаём приватные поля через reflection
        Field requestsField = ScaleService.class.getDeclaredField("requests");
        requestsField.setAccessible(true);
        List<Object> requests = (List<Object>) requestsField.get(service);

        Field eventsField = ScaleService.class.getDeclaredField("events");
        eventsField.setAccessible(true);
        List<Object> events = (List<Object>) eventsField.get(service);

        // Запускаем weightProc в отдельном потоке
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                service.weightProc();
            }
        });
        worker.setDaemon(true);
        worker.start();

        // Добавляем один запрос на чтение веса и будим поток
        synchronized (requests) {
            requests.add(new WeightRequest(1000));
            requests.notifyAll();
        }

        // Ждём появления DataEvent в очереди событий
        DataEvent dataEvent = null;
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && dataEvent == null) {
            synchronized (events) {
                if (!events.isEmpty() && events.get(0) instanceof DataEvent) {
                    dataEvent = (DataEvent) events.get(0);
                    break;
                }
            }
            Thread.sleep(10);
        }

        // Останавливаем поток обработчика
        worker.interrupt();
        worker.join(1000);

        assertNotNull("Ожидалось получение DataEvent в асинхронном режиме", dataEvent);
        assertEquals("Вес в DataEvent.getStatus() должен соответствовать прочитанному весу",
                (int) expectedWeight, dataEvent.getStatus());
    }
}

