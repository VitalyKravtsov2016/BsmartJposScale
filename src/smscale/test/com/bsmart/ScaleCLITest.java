/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart;

import com.bsmart.scale.Pos2Serial;
import com.bsmart.scale.SmScale;
import com.bsmart.tools.StringParams;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

import java.util.prefs.Preferences;

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
     * Test preferences
     */
    @Test
    public void testPreferences() {
        System.out.println("testPreferences");
        try{
        Preferences prefs = Preferences.userNodeForPackage(SmScale.class);
        prefs.put(IDevice.PARAM_PORTNAME, "COM10");
        prefs.flush();
        
        prefs = Preferences.userNodeForPackage(SmScale.class);
        String portName = prefs.get(IDevice.PARAM_PORTNAME, "COM5");
        Assert.assertEquals("COM10", portName);
        }
        catch(Exception e)
        {
            Assert.fail(e.getMessage());
        }
    }
    
    /**
     * Test StringParams
     */
    @Test
    public void testStringParams() {
        System.out.println("testStringParams");
        StringParams params = new StringParams();
        params.set(IDevice.PARAM_PORTNAME, "COM3");
        String portName = params.get(IDevice.PARAM_PORTNAME);
        Assert.assertEquals("COM3", portName);
    }
     
    /**
     * Test Pos2Serial
     */
    @Test
    public void testPos2Serial() {
        System.out.println("testPos2Serial");
        Pos2Serial scale = new Pos2Serial();
        scale.setParam(IDevice.PARAM_PORTNAME, "COM4");
        String portName = scale.getParam(IDevice.PARAM_PORTNAME);
        Assert.assertEquals("COM4", portName);
    }
    
    
    /**
     * Test of main method, of class ScaleCLI.
     */
    @Test
    public void testSaveSettings() {
        System.out.println("testSaveSettings");
        ScaleCLI item = new ScaleCLI();
        item.setPort("COM5");
        item.setBaudrate(115200);
        item.saveSettings();
        
        Assert.assertEquals("COM5", item.getPort());
        Assert.assertEquals(115200, item.getBaudrate());
        
        item = new ScaleCLI();
        Assert.assertEquals("COM5", item.getPort());
        Assert.assertEquals(115200, item.getBaudrate());
        
        Preferences prefs = item.getPreferences();
        String portName = prefs.get(IDevice.PARAM_PORTNAME, "");
        Assert.assertEquals("COM5", portName);
    }
    
}
