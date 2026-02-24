/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.jpos.scale;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import jpos.BaseControl;
import jpos.events.DataEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.JposEvent;
import jpos.events.OutputCompleteEvent;
import jpos.events.StatusUpdateEvent;
import jpos.services.EventCallbacks;

/**
 *
 * @author User
 */
/**
 * Реализация EventCallbacks для тестирования
 */
public class TestEventCallbacks implements EventCallbacks {

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

    public <T extends JposEvent> T waitForEvent(Class<T> eventType, long millis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;

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
