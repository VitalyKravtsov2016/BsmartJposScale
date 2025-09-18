/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.jpos.scale;

import jpos.JposConst;
import jpos.Scale;
import jpos.JposException;
import jpos.ScaleConst;
import jpos.config.JposEntry;
import static jpos.JposConst.*;
import static jpos.ScaleConst.SCAL_SN_DISABLED;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

/**
 *
 * @author Виталий
 */
public class ScaleServiceTest {

    public ScaleServiceTest() {
    }

    private Scale driver;

    @Before
    public void setUp() {
        driver = new Scale();
    }

    @After
    public void tearDown() {
        try {
            driver.close();
        } catch (Exception e) {
        }
        driver = null;
    }

    public void open() throws Exception {
        driver.open("Scale");
    }

    public void claim() throws Exception {
        driver.claim(1000);
    }

    public void enable() throws Exception {
        driver.setDeviceEnabled(true);
    }

    public void openClaimEnable() throws Exception {
        open();
        claim();
        enable();
    }

    /**
     * Test of getCapCompareFirmwareVersion method, of class ScaleService.
     */
    @Test
    public void testGetCapCompareFirmwareVersion() throws Exception {
        System.out.println("getCapCompareFirmwareVersion");
        open();
        boolean expResult = false;
        boolean result = driver.getCapCompareFirmwareVersion();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapStatusUpdate method, of class ScaleService.
     */
    @Test
    public void testGetCapStatusUpdate() throws Exception {
        System.out.println("getCapStatusUpdate");
        open();
        boolean expResult = true;
        boolean result = driver.getCapStatusUpdate();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapUpdateFirmware method, of class ScaleService.
     */
    @Test
    public void testGetCapUpdateFirmware() throws Exception {
        System.out.println("getCapUpdateFirmware");
        open();
        boolean expResult = false;
        boolean result = driver.getCapUpdateFirmware();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapDisplay method, of class ScaleService.
     */
    @Test
    public void testGetCapDisplay() throws Exception {
        System.out.println("getCapDisplay");
        open();
        boolean expResult = false;
        boolean result = driver.getCapDisplay();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapStatisticsReporting method, of class ScaleService.
     */
    @Test
    public void testGetCapStatisticsReporting() throws Exception {
        System.out.println("getCapStatisticsReporting");
        open();
        boolean expResult = false;
        boolean result = driver.getCapStatisticsReporting();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapUpdateStatistics method, of class ScaleService.
     */
    @Test
    public void testGetCapUpdateStatistics() throws Exception {
        System.out.println("getCapUpdateStatistics");
        open();
        boolean expResult = false;
        boolean result = driver.getCapUpdateStatistics();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapDisplayText method, of class ScaleService.
     */
    @Test
    public void testGetCapDisplayText() throws Exception {
        System.out.println("getCapDisplayText");
        open();
        boolean expResult = false;
        boolean result = driver.getCapDisplayText();
        assertEquals(expResult, result);

    }

    /**
     * Test of getCapPowerReporting method, of class ScaleService.
     */
    @Test
    public void testGetCapPowerReporting() throws Exception {
        System.out.println("getCapPowerReporting");
        open();
        int expResult = JPOS_PR_STANDARD;
        int result = driver.getCapPowerReporting();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapPriceCalculating method, of class ScaleService.
     */
    @Test
    public void testGetCapPriceCalculating() throws Exception {
        System.out.println("getCapPriceCalculating");
        open();
        boolean expResult = false;
        boolean result = driver.getCapPriceCalculating();
        assertEquals(expResult, result);
    }

    /**
     * Test of getCapTareWeight method, of class ScaleService.
     */
    @Test
    public void testGetCapTareWeight() throws Exception {
        System.out.println("getCapTareWeight");
        open();
        boolean expResult = true;
        boolean result = driver.getCapTareWeight();
        assertEquals(expResult, result);

    }

    /**
     * Test of getCapZeroScale method, of class ScaleService.
     */
    @Test
    public void testGetCapZeroScale() throws Exception {
        System.out.println("getCapZeroScale");
        open();
        boolean expResult = true;
        boolean result = driver.getCapZeroScale();
        assertEquals(expResult, result);

    }

    /**
     * Test of release method, of class ScaleService.
     */
    @Test
    public void testRelease() throws Exception {
        System.out.println("release");
        open();
        driver.claim(0);
        driver.release();
    }

    /**
     * Test of claim method, of class ScaleService.
     */
    @Test
    public void testClaim() throws Exception {
        System.out.println("claim");
        int timeout = 0;
        open();
        driver.claim(timeout);

    }

    /**
     * Test of close method, of class ScaleService.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        open();
        driver.close();

    }

    /**
     * Test of compareFirmwareVersion method, of class ScaleService.
     */
    @Test
    public void testCompareFirmwareVersion() throws Exception {
        System.out.println("compareFirmwareVersion");
        String arg0 = "";
        int[] arg1 = null;
        open();
        driver.compareFirmwareVersion(arg0, arg1);

    }

    /**
     * Test of getScaleLiveWeight method, of class ScaleService.
     */
    @Test
    public void testGetScaleLiveWeight() throws Exception {
        System.out.println("getScaleLiveWeight");
        open();
        int expResult = 0;
        int result = driver.getScaleLiveWeight();
        assertEquals(expResult, result);

    }

    /**
     * Test of getStatusNotify method, of class ScaleService.
     */
    @Test
    public void testGetStatusNotify() throws Exception {
        System.out.println("getStatusNotify");
        open();
        int expResult = SCAL_SN_DISABLED;
        int result = driver.getStatusNotify();
        assertEquals(expResult, result);

    }

    /**
     * Test of setStatusNotify method, of class ScaleService.
     */
    @Test
    public void testSetStatusNotify() throws Exception {
        System.out.println("setStatusNotify");
        int statusNotify = 0;
        open();
        driver.setStatusNotify(statusNotify);

    }

    /**
     * Test of updateFirmware method, of class ScaleService.
     */
    @Test
    public void testUpdateFirmware() throws Exception {
        System.out.println("updateFirmware");
        String arg0 = "";
        open();
        driver.updateFirmware(arg0);

    }

    /**
     * Test of resetStatistics method, of class ScaleService.
     */
    @Test
    public void testResetStatistics() throws Exception {
        System.out.println("resetStatistics");
        String arg0 = "";
        open();
        driver.resetStatistics(arg0);

    }

    /**
     * Test of retrieveStatistics method, of class ScaleService.
     */
    @Test
    public void testRetrieveStatistics() throws Exception {
        System.out.println("retrieveStatistics");
        String[] arg0 = null;
        open();
        driver.retrieveStatistics(arg0);

    }

    /**
     * Test of updateStatistics method, of class ScaleService.
     */
    @Test
    public void testUpdateStatistics() throws Exception {
        System.out.println("updateStatistics");
        String arg0 = "";
        open();
        driver.updateStatistics(arg0);

    }

    /**
     * Test of clearInput method, of class ScaleService.
     */
    @Test
    public void testClearInput() throws Exception {
        System.out.println("clearInput");
        open();
        driver.clearInput();

    }

    /**
     * Test of displayText method, of class ScaleService.
     */
    @Test
    public void testDisplayText() throws Exception {
        System.out.println("displayText");
        String arg0 = "";
        open();
        driver.displayText(arg0);

    }

    /**
     * Test of setAsyncMode method, of class ScaleService.
     */
    @Test
    public void testSetAsyncMode() throws Exception {
        System.out.println("setAsyncMode");
        boolean async = false;
        open();
        driver.setAsyncMode(async);

    }

    /**
     * Test of getAsyncMode method, of class ScaleService.
     */
    @Test
    public void testGetAsyncMode() throws Exception {
        System.out.println("getAsyncMode");
        open();
        boolean expResult = false;
        boolean result = driver.getAsyncMode();
        assertEquals(expResult, result);

    }

    /**
     * Test of getDataCount method, of class ScaleService.
     */
    @Test
    public void testGetDataCount() throws Exception {
        System.out.println("getDataCount");
        open();
        int expResult = 0;
        int result = driver.getDataCount();
        assertEquals(expResult, result);

    }

    /**
     * Test of getMaxDisplayTextChars method, of class ScaleService.
     */
    @Test
    public void testGetMaxDisplayTextChars() throws Exception {
        System.out.println("getMaxDisplayTextChars");
        open();
        int expResult = 0;
        int result = driver.getMaxDisplayTextChars();
        assertEquals(expResult, result);

    }

    /**
     * Test of getPowerNotify method, of class ScaleService.
     */
    @Test
    public void testGetPowerNotify() throws Exception {
        System.out.println("getPowerNotify");
        open();
        int expResult = 0;
        int result = driver.getPowerNotify();
        assertEquals(expResult, result);

    }

    /**
     * Test of getPowerState method, of class ScaleService.
     */
    @Test
    public void testGetPowerState() throws Exception {
        System.out.println("getPowerState");
        open();
        int expResult = JPOS_PS_UNKNOWN;
        int result = driver.getPowerState();
        assertEquals(expResult, result);
    }

    /**
     * Test of getSalesPrice method, of class ScaleService.
     */
    @Test
    public void testGetSalesPrice() throws Exception {
        System.out.println("getSalesPrice");
        open();
        long expResult = 0L;
        long result = driver.getSalesPrice();
        assertEquals(expResult, result);

    }

    /**
     * Test of getTareWeight method, of class ScaleService.
     */
    @Test
    public void testGetTareWeight() throws Exception {
        System.out.println("getTareWeight");
        open();
        int expResult = 0;
        int result = driver.getTareWeight();
        assertEquals(expResult, result);

    }

    /**
     * Test of getUnitPrice method, of class ScaleService.
     */
    @Test
    public void testGetUnitPrice() throws Exception {
        System.out.println("getUnitPrice");
        open();
        long expResult = 0L;
        long result = driver.getUnitPrice();
        assertEquals(expResult, result);

    }

    /**
     * Test of getAutoDisable method, of class ScaleService.
     */
    @Test
    public void testGetAutoDisable() throws Exception {
        System.out.println("getAutoDisable");
        open();
        boolean expResult = false;
        boolean result = driver.getAutoDisable();
        assertEquals(expResult, result);

    }

    /**
     * Test of setAutoDisable method, of class ScaleService.
     */
    @Test
    public void testSetAutoDisable() throws Exception {
        System.out.println("setAutoDisable");
        boolean autoDisable = false;
        open();
        driver.setAutoDisable(autoDisable);

    }

    /**
     * Test of setDataEventEnabled method, of class ScaleService.
     */
    @Test
    public void testSetDataEventEnabled() throws Exception {
        System.out.println("setDataEventEnabled");
        boolean enabled = false;
        open();
        driver.setDataEventEnabled(enabled);

    }

    /**
     * Test of getDataEventEnabled method, of class ScaleService.
     */
    @Test
    public void testGetDataEventEnabled() throws Exception {
        System.out.println("getDataEventEnabled");
        open();
        boolean expResult = false;
        boolean result = driver.getDataEventEnabled();
        assertEquals(expResult, result);

    }

    /**
     * Test of setPowerNotify method, of class ScaleService.
     */
    @Test
    public void testSetPowerNotify() throws Exception {
        System.out.println("setPowerNotify");
        int powerNotify = 0;
        open();
        driver.setPowerNotify(powerNotify);

    }

    /**
     * Test of setTareWeight method, of class ScaleService.
     */
    @Test
    public void testSetTareWeight() throws Exception {
        System.out.println("setTareWeight");
        int tareWeight = 0;
        openClaimEnable();
        driver.setTareWeight(tareWeight);
    }

    /**
     * Test of setUnitPrice method, of class ScaleService.
     */
    @Test
    public void testSetUnitPrice() throws Exception {
        System.out.println("setUnitPrice");
        long arg0 = 0L;
        open();
        try {
            driver.setUnitPrice(arg0);
            fail("Exception expected");
        } catch (JposException e) {
            assertEquals(JPOS_E_ILLEGAL, e.getErrorCode());
            assertEquals("Не поддерживается", e.getMessage());
        }
    }

    /**
     * Test of zeroScale method, of class ScaleService.
     */
    @Test
    public void testZeroScale() throws Exception {
        System.out.println("zeroScale");
        openClaimEnable();
        driver.zeroScale();

    }

    /**
     * Test of getMaximumWeight method, of class ScaleService.
     */
    @Test
    public void testGetMaximumWeight() throws Exception {
        System.out.println("getMaximumWeight");
        open();
        int expResult = 0x7FFFFFFF;
        int result = driver.getMaximumWeight();
        assertEquals(expResult, result);
    }

    /**
     * Test of getWeightUnit method, of class ScaleService.
     */
    @Test
    public void testGetWeightUnit() throws Exception {
        System.out.println("getWeightUnit");
        open();
        int expResult = ScaleConst.SCAL_WU_GRAM;
        int result = driver.getWeightUnit();
        assertEquals(expResult, result);
    }

    /**
     * Test of readWeight method, of class ScaleService.
     */
    @Test
    public void testReadWeight() throws Exception {
        System.out.println("readWeight");
        int[] data = null;
        int timeout = 0;
        open();
        driver.readWeight(data, timeout);
    }

    /**
     * Test of checkHealth method, of class ScaleService.
     */
    @Test
    public void testCheckHealth() throws Exception {
        System.out.println("checkHealth");
        int arg0 = 0;
        open();
        driver.checkHealth(arg0);

    }

    /**
     * Test of directIO method, of class ScaleService.
     */
    @Test
    public void testDirectIO() throws Exception {
        System.out.println("directIO");
        int arg0 = 0;
        int[] arg1 = null;
        Object arg2 = null;
        open();
        try {
            driver.directIO(arg0, arg1, arg2);
            fail("No exception");
        } catch (JposException e) {
            assertEquals(JPOS_E_ILLEGAL, e.getErrorCode());
            assertEquals("Неизвестная команда", e.getMessage());
        }
    }

    /**
     * Test of getCheckHealthText method, of class ScaleService.
     */
    @Test
    public void testGetCheckHealthText() throws Exception {
        System.out.println("getCheckHealthText");
        open();
        String expResult = "";
        String result = driver.getCheckHealthText();
        assertEquals(expResult, result);

    }

    /**
     * Test of getClaimed method, of class ScaleService.
     */
    @Test
    public void testGetClaimed() throws Exception {
        System.out.println("getClaimed");
        open();
        boolean expResult = false;
        boolean result = driver.getClaimed();
        assertEquals(expResult, result);

    }

    /**
     * Test of getDeviceServiceDescription method, of class ScaleService.
     */
    @Test
    public void testGetDeviceServiceDescription() throws Exception {
        System.out.println("getDeviceServiceDescription");
        open();
        String expResult = "ScalePos2Service";
        String result = driver.getDeviceServiceDescription();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDeviceServiceVersion method, of class ScaleService.
     */
    @Test
    public void testGetDeviceServiceVersion() throws Exception {
        System.out.println("getDeviceServiceVersion");
        open();
        int expResult = 1013003;
        int result = driver.getDeviceServiceVersion();
        assertEquals(expResult, result);

    }

    /**
     * Test of getFreezeEvents method, of class ScaleService.
     */
    @Test
    public void testGetFreezeEvents() throws Exception {
        System.out.println("getFreezeEvents");
        open();
        boolean expResult = false;
        boolean result = driver.getFreezeEvents();
        assertEquals(expResult, result);

    }

    /**
     * Test of setFreezeEvents method, of class ScaleService.
     */
    @Test
    public void testSetFreezeEvents() throws Exception {
        System.out.println("setFreezeEvents");
        boolean freezeEvents = false;
        open();
        driver.setFreezeEvents(freezeEvents);

    }

    /**
     * Test of getPhysicalDeviceDescription method, of class ScaleService.
     */
    @Test
    public void testGetPhysicalDeviceDescription() throws Exception {
        System.out.println("getPhysicalDeviceDescription");
        open();
        String expResult = "Весы ШТРИХ-М POS2";
        String result = driver.getPhysicalDeviceDescription();
        assertEquals(expResult, result);

    }

    /**
     * Test of getPhysicalDeviceName method, of class ScaleService.
     */
    @Test
    public void testGetPhysicalDeviceName() throws Exception {
        System.out.println("getPhysicalDeviceName");
        open();
        String expResult = "Весы ШТРИХ-М POS2";
        String result = driver.getPhysicalDeviceName();
        assertEquals(expResult, result);

    }

    /**
     * Test of getState method, of class ScaleService.
     */
    @Test
    public void testGetState() throws Exception {
        System.out.println("getState");
        open();
        int expResult = JposConst.JPOS_S_IDLE;
        int result = driver.getState();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDeviceEnabled method, of class ScaleService.
     */
    @Test
    public void testGetDeviceEnabled() throws Exception {
        System.out.println("getDeviceEnabled");
        open();
        boolean expResult = false;
        boolean result = driver.getDeviceEnabled();
        assertEquals(expResult, result);

    }

    /**
     * Test of setDeviceEnabled method, of class ScaleService.
     */
    @Test
    public void testSetDeviceEnabled() throws Exception {
        System.out.println("setDeviceEnabled");
        boolean enabled = false;
        open();
        driver.claim(0);
        driver.setDeviceEnabled(enabled);

    }

    /**
     * Test of getZeroValid method, of class ScaleService.
     */
    @Test
    public void testGetZeroValid() throws Exception {
        System.out.println("getZeroValid");
        open();
        boolean expResult = false;
        boolean result = driver.getZeroValid();
        assertEquals(expResult, result);

    }

    /**
     * Test of setZeroValid method, of class ScaleService.
     */
    @Test
    public void testSetZeroValid() throws Exception {
        System.out.println("setZeroValid");
        boolean zeroValid = false;
        open();
        driver.setZeroValid(zeroValid);

    }

}
