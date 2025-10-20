/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart;

import java.util.prefs.Preferences;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author User
 */
public class ScaleCLITest {
    
    public ScaleCLITest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of main method, of class ScaleCLI.
     */
    @Test
    public void testSaveSettings() {
        System.out.println("testSaveSettings");
        ScaleCLI item = new ScaleCLI();
        item.loadSettings();
        item.setPort("COM1");
        item.setBaudrate(115200);
        item.saveSettings();
        
        item = new ScaleCLI();
        item.loadSettings();
        Preferences prefs = item.getPreferences();
        String portName = prefs.get(IDevice.PARAM_PORTNAME, "");
        assertEquals("COM1", portName);
    }
    
}
