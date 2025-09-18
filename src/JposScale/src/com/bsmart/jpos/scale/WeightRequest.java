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
public class WeightRequest {

    private final int timeout;
    
    public WeightRequest(int timeout){
        this.timeout = timeout;
    }
    
    public int getTimeout(){
        return timeout;
    }
}
