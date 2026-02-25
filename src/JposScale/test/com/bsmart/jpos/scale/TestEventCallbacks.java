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
    private int lastErrorResponse = 0;
    private final Object errorEventLock = new Object();

    @Override
    public void fireDataEvent(DataEvent event) {
        eventQueue.offer(event);
    }

    @Override
    public void fireErrorEvent(ErrorEvent event) {
        synchronized (errorEventLock) {
            event.setErrorResponse(lastErrorResponse);
            eventQueue.offer(event);
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

    public void setErrorResponse(int errorResponse) {
        synchronized (errorEventLock) {
            lastErrorResponse = errorResponse;
        }
    }

    public void clearEvents() {
        eventQueue.clear();
    }

    public BaseControl getEventSource() {
        return null;
    }
}
