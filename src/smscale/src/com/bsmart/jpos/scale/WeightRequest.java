/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.jpos.scale;

/**
 *
 * @author Виталий
 */

class WeightRequest {
    private final int timeout;
    private final long timestamp;
    
    public WeightRequest(int timeout) {
        this.timeout = timeout;
        this.timestamp = System.currentTimeMillis();
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}