/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bsmart.jpos.scale;

import jpos.JposException;
import jpos.Scale;
import jpos.JposConst;
import jpos.ScaleConst;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 *
 * @author Виталий
 */

public class ScaleServiceTest2 {
    
    private Scale scale;
    private static boolean jposInitialized = false;
    
    @Before
    public void setUp() throws Exception {
        if (!jposInitialized) {
            initializeJpos();
        }
        
        scale = new Scale();
    }
    
    @After
    public void tearDown() {
        if (scale != null) {
            try {
                if (scale.getClaimed()) {
                    scale.release();
                }
                scale.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
    
    private void initializeJpos() throws Exception {
        System.out.println("\n=== JavaPOS Initialization ===");
        
        // 1. Находим jpos.xml
        File configFile = findJposConfig();
        if (configFile == null) {
            diagnoseJposLocation();
            throw new RuntimeException("Cannot find jpos.xml");
        }
        
        System.out.println("Using config: " + configFile.getAbsolutePath());
        System.out.println("Config exists: " + configFile.exists());
        System.out.println("Config readable: " + configFile.canRead());
        System.out.println("Config size: " + configFile.length() + " bytes");
        
        // 2. Проверяем содержимое XML
        verifyJposXml(configFile);
        
        // 3. Устанавливаем свойства JCL
        System.setProperty("jpos.config.regPopFile", configFile.getAbsolutePath());
        System.setProperty("jpos.config.regPopFileType", "xml");
        System.setProperty("jpos.loader.serviceManagerClass", 
                          "jpos.loader.simple.SimpleServiceManager");
        
        // 4. Проверяем что JCL видит наш сервис
        checkJclRegistry();
        
        jposInitialized = true;
    }
    
    private File findJposConfig() {
        // Приоритет: сначала ищем в build, потом в test/resources
        
        String[] possiblePaths = {
            "build/test/classes/jpos.xml",
            "build/classes/test/jpos.xml",
            "build/test/resources/jpos.xml",
            "test/resources/jpos.xml",
            "JposScale/build/test/classes/jpos.xml",
            "JposScale/test/resources/jpos.xml",
            "src/test/resources/jpos.xml"
        };
        
        String userDir = System.getProperty("user.dir");
        System.out.println("User dir: " + userDir);
        
        for (String path : possiblePaths) {
            File file = new File(userDir, path);
            if (file.exists()) {
                return file;
            }
            
            // Пробуем без userDir
            file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        
        // Пробуем через ClassLoader
        URL url = getClass().getClassLoader().getResource("jpos.xml");
        if (url != null) {
            String path = url.getPath();
            if (path.startsWith("file:/")) {
                path = path.substring(6);
            }
            if (path.startsWith("/") && System.getProperty("os.name").contains("Windows")) {
                path = path.substring(1);
            }
            return new File(path);
        }
        
        return null;
    }
    
    private void verifyJposXml(File configFile) throws Exception {
        System.out.println("\n--- Verifying jpos.xml content ---");
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(configFile);
        
        NodeList entries = doc.getElementsByTagName("JposEntry");
        System.out.println("Found " + entries.getLength() + " JposEntry elements");
        
        for (int i = 0; i < entries.getLength(); i++) {
            org.w3c.dom.Element entry = (org.w3c.dom.Element) entries.item(i);
            String logicalName = entry.getAttribute("logicalName");
            System.out.println("  Entry " + i + ": logicalName = " + logicalName);
            
            // Проверяем factory class
            NodeList creation = entry.getElementsByTagName("creation");
            if (creation.getLength() > 0) {
                org.w3c.dom.Element creationElem = (org.w3c.dom.Element) creation.item(0);
                String factoryClass = creationElem.getAttribute("factoryClass");
                String serviceClass = creationElem.getAttribute("serviceClass");
                System.out.println("    factoryClass: " + factoryClass);
                System.out.println("    serviceClass: " + serviceClass);
                
                // Проверяем доступность классов
                try {
                    Class.forName(factoryClass);
                    System.out.println("    ✓ Factory class found");
                } catch (ClassNotFoundException e) {
                    System.err.println("    ✗ Factory class NOT found: " + factoryClass);
                }
                
                try {
                    Class.forName(serviceClass);
                    System.out.println("    ✓ Service class found");
                } catch (ClassNotFoundException e) {
                    System.err.println("    ✗ Service class NOT found: " + serviceClass);
                }
            }
        }
    }
    
    private void checkJclRegistry() {
        System.out.println("\n--- Checking JCL Registry ---");
        
        try {
            // Загружаем JCL реестр
            jpos.loader.simple.SimpleServiceManager ssm = 
                new jpos.loader.simple.SimpleServiceManager();
            
            // Получаем все logical names
          
            java.util.Enumeration<?> entries = ssm.getRegPopulator().getEntries();
            System.out.println("JCL Registry entries:");
            
            boolean foundScale = false;
            while (entries.hasMoreElements()) {
                Object entry = entries.nextElement();
                if (entry instanceof jpos.config.JposEntry) {
                    jpos.config.JposEntry jposEntry = (jpos.config.JposEntry) entry;
                    String logicalName = (String) jposEntry.getPropertyValue("logicalName");
                    String factoryClass = (String) jposEntry.getPropertyValue("factoryClass");
                    String serviceClass = (String) jposEntry.getPropertyValue("serviceClass");
                    
                    System.out.println("  logicalName: " + logicalName);
                    System.out.println("    factoryClass: " + factoryClass);
                    System.out.println("    serviceClass: " + serviceClass);
                    
                    if ("Scale".equals(logicalName) || "TestScale".equals(logicalName)) {
                        foundScale = true;
                    }
                }
            }
            
            if (!foundScale) {
                System.err.println("  ✗ No Scale service found in registry!");
            } else {
                System.out.println("  ✓ Scale service found in registry");
            }
            
        } catch (Exception e) {
            System.err.println("Error checking JCL registry: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void diagnoseJposLocation() {
        System.err.println("\n=== DIAGNOSTICS ===");
        
        // Проверяем classpath
        System.err.println("Classpath entries:");
        String classpath = System.getProperty("java.class.path");
        String[] entries = classpath.split(File.pathSeparator);
        for (String entry : entries) {
            System.err.println("  " + entry);
            
            // Проверяем наличие jpos.xml в каждой директории classpath
            File entryFile = new File(entry);
            if (entryFile.isDirectory()) {
                File jposFile = new File(entryFile, "jpos.xml");
                if (jposFile.exists()) {
                    System.err.println("    → jpos.xml found here!");
                }
            }
        }
        
        // Проверяем системные свойства
        System.err.println("\nSystem properties:");
        System.err.println("  user.dir: " + System.getProperty("user.dir"));
        System.err.println("  java.home: " + System.getProperty("java.home"));
        
        // Проверяем папку проекта
        File projectDir = new File(".");
        findJposXml(projectDir);
    }
    
    private void findJposXml(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().startsWith(".")) {
                    findJposXml(file);
                }
            } else if (file.getName().equals("jpos.xml")) {
                System.err.println("Found jpos.xml at: " + file.getAbsolutePath());
            }
        }
    }
    
    public void open() throws Exception {
        scale.open("Scale");
    }

    public void claim() throws Exception {
        scale.claim(1000);
    }

    public void enable() throws Exception {
        scale.setDeviceEnabled(true);
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
        boolean result = scale.getCapCompareFirmwareVersion();
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
        boolean result = scale.getCapStatusUpdate();
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
        boolean result = scale.getCapUpdateFirmware();
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
        boolean result = scale.getCapDisplay();
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
        boolean result = scale.getCapStatisticsReporting();
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
        boolean result = scale.getCapUpdateStatistics();
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
        boolean result = scale.getCapDisplayText();
        assertEquals(expResult, result);

    }

    /**
     * Test of getCapPowerReporting method, of class ScaleService.
     */
    @Test
    public void testGetCapPowerReporting() throws Exception {
        System.out.println("getCapPowerReporting");
        open();
        int expResult = JposConst.JPOS_PR_STANDARD;
        int result = scale.getCapPowerReporting();
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
        boolean result = scale.getCapPriceCalculating();
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
        boolean result = scale.getCapTareWeight();
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
        boolean result = scale.getCapZeroScale();
        assertEquals(expResult, result);

    }

    /**
     * Test of release method, of class ScaleService.
     */
    @Test
    public void testRelease() throws Exception {
        System.out.println("release");
        open();
        scale.claim(0);
        scale.release();
    }

    /**
     * Test of claim method, of class ScaleService.
     */
    @Test
    public void testClaim() throws Exception {
        System.out.println("claim");
        int timeout = 0;
        open();
        scale.claim(timeout);

    }

    /**
     * Test of close method, of class ScaleService.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        open();
        scale.close();

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
        scale.compareFirmwareVersion(arg0, arg1);

    }

    /**
     * Test of getScaleLiveWeight method, of class ScaleService.
     */
    @Test
    public void testGetScaleLiveWeight() throws Exception {
        System.out.println("getScaleLiveWeight");
        open();
        int expResult = 0;
        int result = scale.getScaleLiveWeight();
        assertEquals(expResult, result);

    }

    /**
     * Test of getStatusNotify method, of class ScaleService.
     */
    @Test
    public void testGetStatusNotify() throws Exception {
        System.out.println("getStatusNotify");
        open();
        int expResult = ScaleConst.SCAL_SN_DISABLED;
        int result = scale.getStatusNotify();
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
        scale.setStatusNotify(statusNotify);

    }

    /**
     * Test of updateFirmware method, of class ScaleService.
     */
    @Test
    public void testUpdateFirmware() throws Exception {
        System.out.println("updateFirmware");
        String arg0 = "";
        open();
        scale.updateFirmware(arg0);

    }

    /**
     * Test of resetStatistics method, of class ScaleService.
     */
    @Test
    public void testResetStatistics() throws Exception {
        System.out.println("resetStatistics");
        String arg0 = "";
        open();
        scale.resetStatistics(arg0);

    }

    /**
     * Test of retrieveStatistics method, of class ScaleService.
     */
    @Test
    public void testRetrieveStatistics() throws Exception {
        System.out.println("retrieveStatistics");
        String[] arg0 = null;
        open();
        scale.retrieveStatistics(arg0);

    }

    /**
     * Test of updateStatistics method, of class ScaleService.
     */
    @Test
    public void testUpdateStatistics() throws Exception {
        System.out.println("updateStatistics");
        String arg0 = "";
        open();
        scale.updateStatistics(arg0);

    }

    /**
     * Test of clearInput method, of class ScaleService.
     */
    @Test
    public void testClearInput() throws Exception {
        System.out.println("clearInput");
        open();
        scale.clearInput();

    }

    /**
     * Test of displayText method, of class ScaleService.
     */
    @Test
    public void testDisplayText() throws Exception {
        System.out.println("displayText");
        String arg0 = "";
        open();
        scale.displayText(arg0);

    }

    /**
     * Test of setAsyncMode method, of class ScaleService.
     */
    @Test
    public void testSetAsyncMode() throws Exception {
        System.out.println("setAsyncMode");
        boolean async = false;
        open();
        scale.setAsyncMode(async);

    }

    /**
     * Test of getAsyncMode method, of class ScaleService.
     */
    @Test
    public void testGetAsyncMode() throws Exception {
        System.out.println("getAsyncMode");
        open();
        boolean expResult = false;
        boolean result = scale.getAsyncMode();
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
        int result = scale.getDataCount();
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
        int result = scale.getMaxDisplayTextChars();
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
        int result = scale.getPowerNotify();
        assertEquals(expResult, result);

    }

    /**
     * Test of getPowerState method, of class ScaleService.
     */
    @Test
    public void testGetPowerState() throws Exception {
        System.out.println("getPowerState");
        open();
        int expResult = JposConst.JPOS_PS_UNKNOWN;
        int result = scale.getPowerState();
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
        long result = scale.getSalesPrice();
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
        int result = scale.getTareWeight();
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
        long result = scale.getUnitPrice();
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
        boolean result = scale.getAutoDisable();
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
        scale.setAutoDisable(autoDisable);

    }

    /**
     * Test of setDataEventEnabled method, of class ScaleService.
     */
    @Test
    public void testSetDataEventEnabled() throws Exception {
        System.out.println("setDataEventEnabled");
        boolean enabled = false;
        open();
        scale.setDataEventEnabled(enabled);

    }

    /**
     * Test of getDataEventEnabled method, of class ScaleService.
     */
    @Test
    public void testGetDataEventEnabled() throws Exception {
        System.out.println("getDataEventEnabled");
        open();
        boolean expResult = false;
        boolean result = scale.getDataEventEnabled();
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
        scale.setPowerNotify(powerNotify);

    }

    /**
     * Test of setTareWeight method, of class ScaleService.
     */
    @Test
    public void testSetTareWeight() throws Exception {
        System.out.println("setTareWeight");
        int tareWeight = 0;
        openClaimEnable();
        scale.setTareWeight(tareWeight);
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
            scale.setUnitPrice(arg0);
            fail("Exception expected");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
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
        scale.zeroScale();

    }

    /**
     * Test of getMaximumWeight method, of class ScaleService.
     */
    @Test
    public void testGetMaximumWeight() throws Exception {
        System.out.println("getMaximumWeight");
        open();
        int expResult = 0x7FFFFFFF;
        int result = scale.getMaximumWeight();
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
        int result = scale.getWeightUnit();
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
        scale.readWeight(data, timeout);
    }

    /**
     * Test of checkHealth method, of class ScaleService.
     */
    @Test
    public void testCheckHealth() throws Exception {
        System.out.println("checkHealth");
        int arg0 = 0;
        open();
        scale.checkHealth(arg0);

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
            scale.directIO(arg0, arg1, arg2);
            fail("No exception");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
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
        String result = scale.getCheckHealthText();
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
        boolean result = scale.getClaimed();
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
        String result = scale.getDeviceServiceDescription();
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
        int result = scale.getDeviceServiceVersion();
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
        boolean result = scale.getFreezeEvents();
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
        scale.setFreezeEvents(freezeEvents);

    }

    /**
     * Test of getPhysicalDeviceDescription method, of class ScaleService.
     */
    @Test
    public void testGetPhysicalDeviceDescription() throws Exception {
        System.out.println("getPhysicalDeviceDescription");
        open();
        String expResult = "Весы ШТРИХ-М POS2";
        String result = scale.getPhysicalDeviceDescription();
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
        String result = scale.getPhysicalDeviceName();
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
        int result = scale.getState();
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
        boolean result = scale.getDeviceEnabled();
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
        scale.claim(0);
        scale.setDeviceEnabled(enabled);

    }

    /**
     * Test of getZeroValid method, of class ScaleService.
     */
    @Test
    public void testGetZeroValid() throws Exception {
        System.out.println("getZeroValid");
        open();
        boolean expResult = false;
        boolean result = scale.getZeroValid();
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
        scale.setZeroValid(zeroValid);

    }

}
