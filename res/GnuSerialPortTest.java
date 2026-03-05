/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.jpos.scale;

import com.bsmart.IDevice;
import com.bsmart.jpos.JposUtils;
import com.bsmart.port.GnuSerialPort;
import jpos.JposConst;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import com.bsmart.port.GnuSerialPort;
import static org.junit.Assert.*;

/**
 *
 * @author User
 */
public class GnuSerialPortTest {

    private String portName = "COM3";
    //private String portName = "/dev/ttyACM0"; // или /dev/ttyUSB0, /dev/ttyS0
    
    @Test
    public void testOpen() {
        GnuSerialPort port = new GnuSerialPort();
        port.portName = portName;
        port.appName = "GnuSerialPortTest";
        try {

            for (int i = 0; i < 10; i++) {
                port.open(0);
                port.close();
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testOpenTimeout1000() {
        GnuSerialPort port = new GnuSerialPort();
        port.portName = portName;
        port.appName = "GnuSerialPortTest";
        try {

            for (int i = 0; i < 10; i++) {
                port.open(1000);
                port.close();
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
