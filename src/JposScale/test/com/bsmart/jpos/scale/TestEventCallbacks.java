package com.bsmart.jpos.scale;

import jpos.BaseControl;
import jpos.events.DataEvent;
import jpos.events.ErrorEvent;
import jpos.events.JposEvent;
import jpos.events.StatusUpdateEvent;
import jpos.services.EventCallbacks;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestEventCallbacks implements EventCallbacks {

    private final BlockingQueue<JposEvent> eventQueue = new LinkedBlockingQueue<>();
    private ErrorEvent lastErrorEvent = null;
    private final Object errorEventLock = new Object();

    @Override
    public void fireDataEvent(DataEvent event) {
        eventQueue.offer(event);
    }

    @Override
    public void fireErrorEvent(ErrorEvent event) {
        synchronized (errorEventLock) {
            lastErrorEvent = event;
            eventQueue.offer(event);
            errorEventLock.notifyAll(); // Уведомляем об получении ErrorEvent
        }
    }

    @Override
    public void fireOutputCompleteEvent(jpos.events.OutputCompleteEvent event) {
        eventQueue.offer(event);
    }

    @Override
    public void fireStatusUpdateEvent(StatusUpdateEvent event) {
        eventQueue.offer(event);
    }

    @Override
    public void fireDirectIOEvent(jpos.events.DirectIOEvent event) {
        eventQueue.offer(event);
    }

    public <T extends JposEvent> T waitForEvent(Class<T> eventClass, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            JposEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
            if (event != null && eventClass.isAssignableFrom(event.getClass())) {
                return eventClass.cast(event);
            }
        }
        return null;
    }

    public ErrorEvent waitForErrorEvent(long timeoutMs) throws InterruptedException {
        synchronized (errorEventLock) {
            if (lastErrorEvent != null) {
                return lastErrorEvent;
            }
            errorEventLock.wait(timeoutMs);
            return lastErrorEvent;
        }
    }

    public void setErrorResponse(int errorResponse) {
        synchronized (errorEventLock) {
            if (lastErrorEvent != null) {
                lastErrorEvent.setErrorResponse(errorResponse);
                synchronized (lastErrorEvent) {
                    lastErrorEvent.notifyAll(); // Уведомляем сервис
                }
            }
        }
    }

    public void clearEvents() {
        eventQueue.clear();
        synchronized (errorEventLock) {
            lastErrorEvent = null;
        }
    }

    public ErrorEvent getLastErrorEvent() {
        synchronized (errorEventLock) {
            return lastErrorEvent;
        }
    }

    public BaseControl getEventSource() {
        return null;
    }
}
