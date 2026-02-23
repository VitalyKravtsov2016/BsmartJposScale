package com.bsmart.jpos.scale;

import com.bsmart.scale.*;
import com.bsmart.tools.StringParams;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Тестовая реализация ScaleSerial для использования в тестах.
 * Позволяет программировать поведение без Mockito.
 */
public class TestScaleSerial extends ScaleSerial {
    
    // Состояние соединения
    private boolean connected = false;
    private StringParams params;
    
    // Управление поведением
    private ScaleWeight currentWeight;
    private Exception nextException;
    private DeviceMetrics deviceMetrics;
    private EScale type = EScale.Pos2;
    
    // История вызовов для проверок
    private final BlockingQueue<Long> tareCalls = new LinkedBlockingQueue<>();
    private int zeroCallCount = 0;
    
    // Задержки для эмуляции реального устройства
    private long responseDelay = 0;
    
    public TestScaleSerial() {
        // По умолчанию возвращаем стабильный вес
        ScaleStatus status = new ScaleStatus(0x10);
        this.currentWeight = new ScaleWeight(0, 0, status);
        this.deviceMetrics = new DeviceMetrics();
    }
    
    // ========== Методы управления для тестов ==========
    
    public void setCurrentWeight(long weight, boolean stable, boolean overweight) {
        int statusBits = 0;
        if (stable) statusBits |= 0x10;
        if (overweight) statusBits |= 0x40;
        
        ScaleStatus status = new ScaleStatus(statusBits);
        this.currentWeight = new ScaleWeight(weight, 0, status);
    }
    
    public void setCurrentWeight(ScaleWeight weight) {
        this.currentWeight = weight;
    }
    
    public void setNextException(Exception exception) {
        this.nextException = exception;
    }
    
    public void setResponseDelay(long millis) {
        this.responseDelay = millis;
    }
    
    public void setDeviceType(EScale type) {
        this.type = type;
    }
    
    public void setDeviceMetrics(DeviceMetrics metrics) {
        this.deviceMetrics = metrics;
    }
    
    // Методы для проверки вызовов
    public int getZeroCallCount() {
        return zeroCallCount;
    }
    
    public BlockingQueue<Long> getTareCalls() {
        return tareCalls;
    }
    
    public Long getLastTare() throws InterruptedException {
        return tareCalls.poll(100, TimeUnit.MILLISECONDS);
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    // ========== Реализация методов ScaleSerial ==========
    
    @Override
    public void setParams(StringParams params) {
        this.params = params;
    }
    
    @Override
    public void connect() throws Exception {
        if (nextException != null) {
            Exception e = nextException;
            nextException = null;
            throw e;
        }
        connected = true;
    }
    
    @Override
    public void disconnect() {
        connected = false;
    }
    
    @Override
    public ScaleWeight getWeight() throws Exception {
        if (responseDelay > 0) {
            Thread.sleep(responseDelay);
        }
        
        if (nextException != null) {
            Exception e = nextException;
            nextException = null;
            throw e;
        }
        
        return currentWeight;
    }
    
    @Override
    public void zero() throws Exception {
        zeroCallCount++;
    }
    
    @Override
    public void tara(long tare) throws Exception {
        tareCalls.offer(tare);
    }
    
    @Override
    public DeviceMetrics getDeviceMetrics() throws Exception {
        return deviceMetrics;
    }
    
    @Override
    public EScale getType() {
        return type;
    }
}