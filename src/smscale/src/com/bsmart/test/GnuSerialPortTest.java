/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.test;

import com.bsmart.port.GnuSerialPort;
import jpos.Scale;

/**
 *
 * @author User
 */
public class GnuSerialPortTest {
    
    //private String portName = "COM3";
    private String portName = "/dev/ttyACM0";
    
    public void testOpen() {
        System.out.print("testOpen...");
        GnuSerialPort port = new GnuSerialPort();
        port.portName = portName;
        port.appName = "GnuSerialPortTest";
        try {

            for (int i = 0; i < 10; i++) {
                port.open(0);
                port.close();
            }
            System.out.println("OK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testOpenTimeout1000() {
        System.out.print("testOpenTimeout1000...");
        GnuSerialPort port = new GnuSerialPort();
        port.portName = portName;
        port.appName = "GnuSerialPortTest";
        try {

            for (int i = 0; i < 10; i++) {
                port.open(1000);
                port.close();
            }
            System.out.println("OK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testOpenRelease() {
        System.out.print("testOpenRelease...");
        
        Scale scale = new Scale();
        int[] weightData = new int[1];
        try {
            for (int i = 0; i < 10; i++) {
                scale.open("Scale");
                scale.claim(2000);
                scale.setDeviceEnabled(true);
                scale.readWeight(weightData, 1000);
                scale.setDeviceEnabled(false);
                scale.release();
                scale.close();
            }
            System.out.println("OK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //static
    /**
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Test GnuSerialPort open/close");
        
        GnuSerialPortTest test = new GnuSerialPortTest();
        test.testOpen();
        test.testOpenTimeout1000();
        test.testOpenRelease();
    }
}
