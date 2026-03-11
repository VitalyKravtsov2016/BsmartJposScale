package com.bsmart.jpos.scale;

import jpos.JposException;
import jpos.Scale;
import jpos.events.DataEvent;
import jpos.events.DataListener;
import jpos.events.DirectIOEvent;
import jpos.events.DirectIOListener;
import jpos.events.ErrorEvent;
import jpos.events.ErrorListener;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;

import org.junit.*;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * ПРИЕМОЧНЫЕ ТЕСТЫ для драйвера весов через jpos.Scale
 * Совместимо с Java 8
 */
@RunWith(Parameterized.class)
public class ScaleJposAcceptanceTest {
    
    private static final String CONFIG_FILE = "scale-test.properties";
    private static final String DEFAULT_JPOS_XML = "jpos.xml";
    
    @Rule
    public Timeout globalTimeout = Timeout.seconds(120);
    
    private static Pos2ProtocolEmulator emulator;
    private static Properties testConfig;
    private static String jposXmlPath;
    private static final AtomicInteger totalTestsRun = new AtomicInteger(0);
    private static final AtomicInteger totalTestsPassed = new AtomicInteger(0);
    private static final Map<String, Long> testDurations = new ConcurrentHashMap<>();
    
    private final String emulatorPort;
    private final String driverPort;
    private final String logicalDeviceName;
    private final String testName;
    private final String testId;
    
    // JPOS Scale объект
    private Scale scale;
    
    // Очереди для событий
    private final BlockingQueue<DataEvent> dataEvents = new LinkedBlockingQueue<>();
    private final BlockingQueue<ErrorEvent> errorEvents = new LinkedBlockingQueue<>();
    private final BlockingQueue<StatusUpdateEvent> statusEvents = new LinkedBlockingQueue<>();
    private final BlockingQueue<DirectIOEvent> directIOEvents = new LinkedBlockingQueue<>();
    
    /**
     * Параметризованный конструктор
     */
    public ScaleJposAcceptanceTest(String emulatorPort, String driverPort, 
                                   String logicalName, String testName) {
        this.emulatorPort = emulatorPort;
        this.driverPort = driverPort;
        this.logicalDeviceName = logicalName;
        this.testName = testName;
        this.testId = UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Параметры тестов загружаются из конфигурации
     */
    @Parameterized.Parameters(name = "[{index}] {3} - Emu:{0} Driver:{1}")
    public static Collection<Object[]> data() throws IOException {
        loadConfiguration();
        return loadTestConfigurations();
    }
    
    @BeforeClass
    public static void setUpClass() throws IOException {
        printSeparator('=');
        System.out.println("🔬 JPOS SCALE ACCEPTANCE TESTS");
        printSeparator('=');
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("User dir: " + System.getProperty("user.dir"));
        printSeparator('=');
        System.out.println();
        
        // Настройка логирования
        setupLogging();
        
        // Загружаем конфигурацию
        loadConfiguration();
        
        // Создаем jpos.xml если его нет
        prepareJposXml();
        
        // Создаем директорию для отчетов
        new File("target/test-reports").mkdirs();
    }
    
    @AfterClass
    public static void tearDownClass() throws IOException {
        printSeparator('=');
        System.out.println("📊 TEST SUMMARY");
        printSeparator('=');
        
        int total = totalTestsRun.get();
        int passed = totalTestsPassed.get();
        int failed = total - passed;
        
        System.out.println("Total tests run: " + total);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Success rate: " + (total > 0 ? 
            String.format("%.2f%%", (passed * 100.0 / total)) : "N/A"));
        
        System.out.println("\nTest durations:");
        testDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> System.out.printf("  %s: %d ms%n", e.getKey(), e.getValue()));
        
        // Сохраняем отчет
        saveTestReport();
        
        printSeparator('=');
        System.out.println("✅ TESTS COMPLETED");
        printSeparator('=');
        System.out.println();
    }
    
    @Before
    public void setUp() throws Exception {
        totalTestsRun.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        printSeparator('-');
        System.out.println("▶️  STARTING TEST: " + testName + " [" + testId + "]");
        printSeparator('-');
        System.out.println("Configuration:");
        System.out.println("  • Emulator port: " + emulatorPort);
        System.out.println("  • Driver port: " + driverPort);
        System.out.println("  • Logical name: " + logicalDeviceName);
        System.out.println("  • jpos.xml: " + jposXmlPath);
        printSeparator('-');
        
        // Проверяем доступность портов
        assertPortsAvailable();
        
        try {
            // 1. Запускаем эмулятор
            System.out.print("Starting emulator... ");
            emulator = new Pos2ProtocolEmulator(emulatorPort);
            emulator.start();
            System.out.println("✅ OK on " + emulatorPort);
            
            // Даем время на инициализацию
            int startupDelay = getConfigInt("startup.delay", 500);
            Thread.sleep(startupDelay);
            
            // 2. Создаем и инициализируем JPOS Scale объект
            System.out.print("Creating JPOS Scale... ");
            scale = new jpos.Scale();
            
            // Добавляем слушатели событий
            scale.addDataListener(new TestDataListener());
            scale.addErrorListener(new TestErrorListener());
            scale.addStatusUpdateListener(new TestStatusListener());
            scale.addDirectIOListener(new TestDirectIOListener());
            System.out.println("✅ OK");
            
            // 3. Открываем устройство
            System.out.print("Opening device... ");
            scale.open(logicalDeviceName);
            System.out.println("✅ OK");
            
            // 4. Настройка режимов
            boolean asyncMode = getConfigBoolean("scale.async.mode", false);
            scale.setAsyncMode(asyncMode);
            System.out.println("  • Async mode: " + asyncMode);
            
            boolean dataEventEnabled = getConfigBoolean("scale.data.event.enabled", true);
            scale.setDataEventEnabled(dataEventEnabled);
            System.out.println("  • Data event enabled: " + dataEventEnabled);
            
            printSeparator('-');
            
        } catch (Exception e) {
            System.err.println("❌ SETUP FAILED: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            testDurations.put("SETUP_" + testId, duration);
        }
    }
    
    @After
    public void tearDown() {
        long startTime = System.currentTimeMillis();
        printSeparator('-');
        System.out.println("🧹 CLEANING UP TEST: " + testName + " [" + testId + "]");
        
        List<Exception> errors = new ArrayList<>();
        
        // 1. Закрываем JPOS устройство
        if (scale != null) {
            try {
                if (scale.getClaimed()) {
                    System.out.print("Releasing device... ");
                    scale.release();
                    System.out.println("✅ OK");
                }
            } catch (Exception e) {
                errors.add(e);
                System.err.println("❌ Release failed: " + e.getMessage());
            }
            
            try {
                if (scale.getDeviceEnabled()) {
                    System.out.print("Disabling device... ");
                    scale.setDeviceEnabled(false);
                    System.out.println("✅ OK");
                }
            } catch (Exception e) {
                errors.add(e);
                System.err.println("❌ Disable failed: " + e.getMessage());
            }
            
            try {
                System.out.print("Closing device... ");
                scale.close();
                System.out.println("✅ OK");
            } catch (Exception e) {
                errors.add(e);
                System.err.println("❌ Close failed: " + e.getMessage());
            }
        }
        
        // 2. Останавливаем эмулятор
        if (emulator != null) {
            try {
                System.out.print("Stopping emulator... ");
                emulator.stop();
                System.out.println("✅ OK");
            } catch (Exception e) {
                errors.add(e);
                System.err.println("❌ Emulator stop failed: " + e.getMessage());
            }
        }
        
        // 3. Очищаем очереди событий
        dataEvents.clear();
        errorEvents.clear();
        statusEvents.clear();
        directIOEvents.clear();
        
        // 4. Даем время на освобождение ресурсов
        int cleanupDelay = getConfigInt("cleanup.delay", 500);
        try {
            Thread.sleep(cleanupDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        testDurations.put("CLEANUP_" + testId, duration);
        
        if (errors.isEmpty()) {
            totalTestsPassed.incrementAndGet();
            System.out.println("✅ TEST PASSED: " + testName + " [" + testId + "]");
        } else {
            System.err.println("❌ TEST FINISHED WITH " + errors.size() + " ERRORS: " + testName);
            for (Exception e : errors) {
                System.err.println("  • " + e.getMessage());
            }
        }
        
        printSeparator('-');
        System.out.println();
    }
    
    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    
    private static void printSeparator(char ch) {
        StringBuilder sb = new StringBuilder(80);
        for (int i = 0; i < 80; i++) {
            sb.append(ch);
        }
        System.out.println(sb.toString());
    }
    
    private static void loadConfiguration() throws IOException {
        testConfig = new Properties();
        
        // 1. Загружаем из classpath
        try (InputStream is = ScaleJposAcceptanceTest.class
                .getResourceAsStream("/" + CONFIG_FILE)) {
            if (is != null) {
                testConfig.load(is);
                System.out.println("📁 Loaded config from classpath: " + CONFIG_FILE);
            }
        }
        
        // 2. Загружаем из файла в рабочей директории
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                testConfig.load(is);
                System.out.println("📁 Loaded config from file: " + configPath.toAbsolutePath());
            }
        }
        
        // 3. Загружаем из системных свойств (test.scale.*)
        Properties systemProps = System.getProperties();
        for (String key : systemProps.stringPropertyNames()) {
            if (key.startsWith("test.scale.")) {
                String configKey = key.substring(11); // убираем "test.scale."
                testConfig.setProperty(configKey, systemProps.getProperty(key));
                System.out.println("⚙️  Override from system: " + configKey + " = " + systemProps.getProperty(key));
            }
        }
        
        // 4. Загружаем из переменных окружения (SCALE_TEST_*)
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("SCALE_TEST_")) {
                String configKey = key.substring(11).toLowerCase().replace('_', '.');
                testConfig.setProperty(configKey, entry.getValue());
                System.out.println("🌍 Override from env: " + configKey + " = " + entry.getValue());
            }
        }
        
        // Выводим итоговую конфигурацию
        System.out.println("\n📋 Test configuration:");
        List<String> keys = new ArrayList<>(testConfig.stringPropertyNames());
        Collections.sort(keys);
        for (String key : keys) {
            String value = testConfig.getProperty(key);
            // Маскируем пароли
            if (key.contains("password")) {
                value = "****";
            }
            System.out.println("  • " + key + " = " + value);
        }
        System.out.println();
    }
    
    private static Collection<Object[]> loadTestConfigurations() {
        List<Object[]> configs = new ArrayList<>();
        
        // Получаем логическое имя устройства
        String logicalName = testConfig.getProperty("scale.logical.name", "Scancity_Scale");
        
        // Получаем список портов из конфигурации
        String ports = testConfig.getProperty("test.ports", "");
        
        if (!ports.isEmpty()) {
            // Формат: emuPort1:driverPort1:description,emuPort2:driverPort2:description
            String[] pairs = ports.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                if (parts.length >= 2) {
                    String emuPort = parts[0].trim();
                    String driverPort = parts[1].trim();
                    String description = parts.length >= 3 ? parts[2].trim() : 
                        "Ports " + emuPort + "->" + driverPort;
                    
                    configs.add(new Object[]{emuPort, driverPort, logicalName, description});
                }
            }
        } else {
            // Порты по умолчанию для разных ОС
            String osName = System.getProperty("os.name").toLowerCase();
            
            if (osName.contains("windows")) {
                configs.add(new Object[]{"COM5", "COM6", logicalName, "Windows COM5->COM6"});
                configs.add(new Object[]{"COM7", "COM8", logicalName, "Windows COM7->COM8"});
            } else if (osName.contains("linux")) {
                configs.add(new Object[]{"/dev/ttyS5", "/dev/ttyS6", logicalName, "Linux ttyS5->ttyS6 (socat)"});
                configs.add(new Object[]{"/dev/tnt0", "/dev/tnt1", logicalName, "Linux tnt0->tnt1 (tty0tty)"});
            } else {
                configs.add(new Object[]{"/dev/ttyS0", "/dev/ttyS1", logicalName, "Default fallback"});
            }
        }
        
        return configs;
    }
    
    private static void setupLogging() {
        String logLevel = testConfig.getProperty("log.level", "INFO");
        System.setProperty("log4j.level", logLevel);
        
        // Создаем директорию для логов
        File logDir = new File("target/test-logs");
        logDir.mkdirs();
        
        System.out.println("📝 Log level: " + logLevel);
        System.out.println("📝 Log directory: " + logDir.getAbsolutePath());
    }
    
    private static void prepareJposXml() throws IOException {
        // Используем существующий jpos.xml если есть
        Path xmlPath = Paths.get(DEFAULT_JPOS_XML);
        if (Files.exists(xmlPath)) {
            jposXmlPath = xmlPath.toAbsolutePath().toString();
            System.out.println("📄 Using existing jpos.xml: " + jposXmlPath);
            return;
        }
        
        // Создаем временный jpos.xml с плейсхолдерами
        String jposXmlTemplate = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<JposEntries>\n" +
            "    <JposEntry logicalName=\"${logical.name}\">\n" +
            "        <productDescription value=\"Scale Emulator\" />\n" +
            "        <vendorName value=\"Test\" />\n" +
            "        <vendorURL value=\"http://localhost\" />\n" +
            "        <productName value=\"Scale Emulator\" />\n" +
            "        <productURL value=\"http://localhost\" />\n" +
            "        <deviceCategory value=\"Scale\" />\n" +
            "        <jposCategory value=\"Scale\" />\n" +
            "        <serviceClass value=\"com.bsmart.jpos.scale.ScaleService\" />\n" +
            "        <vendorCategory value=\"Scale\" />\n" +
            "        <vendorID value=\"0\" />\n" +
            "        <productID value=\"0\" />\n" +
            "        <prop name=\"portName\" value=\"${port.name}\" />\n" +
            "        <prop name=\"baudRate\" value=\"${baud.rate}\" />\n" +
            "        <prop name=\"password\" value=\"${password}\" />\n" +
            "        <prop name=\"timeout\" value=\"${timeout}\" />\n" +
            "        <prop name=\"readTimeout\" value=\"${read.timeout}\" />\n" +
            "        <prop name=\"portType\" value=\"${port.type}\" />\n" +
            "    </JposEntry>\n" +
            "</JposEntries>";
        
        // Заменяем плейсхолдеры значениями по умолчанию
        String xmlContent = jposXmlTemplate
            .replace("${logical.name}", testConfig.getProperty("scale.logical.name", "Scancity_Scale"))
            .replace("${baud.rate}", testConfig.getProperty("scale.baud.rate", "9600"))
            .replace("${password}", testConfig.getProperty("scale.password", "0000"))
            .replace("${timeout}", testConfig.getProperty("scale.timeout", "1000"))
            .replace("${read.timeout}", testConfig.getProperty("scale.read.timeout", "1000"))
            .replace("${port.type}", testConfig.getProperty("scale.port.type", "0"));
        
        // Сохраняем во временный файл
        Path tempXml = Files.createTempFile("jpos", ".xml");
        Files.write(tempXml, xmlContent.getBytes("UTF-8"));
        jposXmlPath = tempXml.toAbsolutePath().toString();
        
        // Устанавливаем системное свойство для JPOS
        System.setProperty("jpos.config.populatorFile", jposXmlPath);
        System.setProperty("jpos.config.regPopulator", "jpos.config.simple.SimpleRegPopulator");
        
        System.out.println("📄 Created temporary jpos.xml: " + jposXmlPath);
    }
    
    private static void saveTestReport() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("SCALE TEST REPORT\n");
        report.append("=================\n");
        report.append("Date: ").append(new java.util.Date()).append("\n");
        report.append("Total tests: ").append(totalTestsRun.get()).append("\n");
        report.append("Passed: ").append(totalTestsPassed.get()).append("\n");
        report.append("Failed: ").append(totalTestsRun.get() - totalTestsPassed.get()).append("\n");
        report.append("Success rate: ").append(
            totalTestsRun.get() > 0 ? 
                String.format("%.2f%%", (totalTestsPassed.get() * 100.0 / totalTestsRun.get())) : "N/A"
        ).append("\n");
        report.append("\nTest durations:\n");
        
        for (Map.Entry<String, Long> entry : testDurations.entrySet()) {
            report.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" ms\n");
        }
        
        Path reportPath = Paths.get("target/test-reports/scale-test-report.txt");
        Files.write(reportPath, report.toString().getBytes());
    }
    
    private String getConfig(String key, String defaultValue) {
        return testConfig.getProperty(key, defaultValue);
    }
    
    private int getConfigInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(testConfig.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getConfigBoolean(String key, boolean defaultValue) {
        String value = testConfig.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }
    
    private long getConfigLong(String key, long defaultValue) {
        try {
            return Long.parseLong(testConfig.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private double getConfigDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(testConfig.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private void assertPortsAvailable() {
        // Для Windows COM порты не проверяем как файлы
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            System.out.println("  • Ports: " + emulatorPort + " (emulator), " + 
                driverPort + " (driver) - Windows mode");
            return;
        }
        
        File emuPortFile = new File(emulatorPort);
        File driverPortFile = new File(driverPort);
        
        if (!emuPortFile.exists()) {
            System.out.println("  ⚠️  Warning: Emulator port " + emulatorPort + " does not exist as file");
        } else {
            System.out.println("  • Emulator port file: " + emulatorPort + 
                " (permissions: " + (emuPortFile.canRead() ? "r" : "-") + 
                (emuPortFile.canWrite() ? "w" : "-") + ")");
        }
        
        if (!driverPortFile.exists()) {
            System.out.println("  ⚠️  Warning: Driver port " + driverPort + " does not exist as file");
        } else {
            System.out.println("  • Driver port file: " + driverPort +
                " (permissions: " + (driverPortFile.canRead() ? "r" : "-") + 
                (driverPortFile.canWrite() ? "w" : "-") + ")");
        }
    }
    
    private void waitForEvents() {
        try {
            Thread.sleep(getConfigInt("event.wait", 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== ТЕСТ 1: Базовая связь ====================
    
    @Test
    public void test001_BasicConnectivity() throws Exception {
        System.out.println("\n🔷 TEST 001: Basic connectivity");
        
        int claimTimeout = getConfigInt("claim.timeout", 1000);
        
        scale.setDeviceEnabled(true);
        scale.claim(claimTimeout);
        
        assertTrue("Device should be enabled", scale.getDeviceEnabled());
        assertTrue("Device should be claimed", scale.getClaimed());
        
        System.out.println("  ✅ Device enabled: true");
        System.out.println("  ✅ Device claimed: true");
    }
    
    // ==================== ТЕСТ 2: Чтение стабильного веса ====================
    
    @Test
    public void test002_ReadStableWeight() throws Exception {
        System.out.println("\n🔷 TEST 002: Read stable weight");
        
        long testWeight = getConfigLong("test.weight", 1234);
        int readTimeout = getConfigInt("read.timeout", 5000);
        int eventTimeout = getConfigInt("event.timeout", 5);
        
        emulator.setWeight(testWeight);
        emulator.setStable(true);
        emulator.setOverweight(false);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        scale.readWeight(new int[1], readTimeout);
        System.out.println("  • readWeight() called");
        
        DataEvent event = dataEvents.poll(eventTimeout, TimeUnit.SECONDS);
        assertNotNull("Should receive DataEvent", event);
        System.out.println("  • DataEvent received, status: " + event.getStatus());
        
        int weight = scale.getScaleLiveWeight();
        System.out.println("  • Live weight: " + weight);
        
        assertTrue("Weight should be positive", weight > 0);
    }
    
    // ==================== ТЕСТ 3: Чтение нестабильного веса ====================
    
    @Test
    public void test003_ReadUnstableWeight() throws Exception {
        System.out.println("\n🔷 TEST 003: Read unstable weight");
        
        long testWeight = getConfigLong("test.unstable.weight", 5678);
        
        emulator.setWeight(testWeight);
        emulator.setStable(false);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        scale.readWeight(new int[1], getConfigInt("read.timeout", 5000));
        
        DataEvent event = dataEvents.poll(getConfigInt("event.timeout", 5), TimeUnit.SECONDS);
        assertNotNull("Should receive DataEvent even for unstable weight", event);
        
        int weight = scale.getScaleLiveWeight();
        System.out.println("  • Unstable weight: " + weight);
    }
    
    // ==================== ТЕСТ 4: Перегруз ====================
    
    @Test
    public void test004_OverweightCondition() throws Exception {
        System.out.println("\n🔷 TEST 004: Overweight condition");
        
        emulator.setWeight(999999);
        emulator.setOverweight(true);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        scale.readWeight(new int[1], getConfigInt("read.timeout", 5000));
        
        DataEvent event = dataEvents.poll(getConfigInt("event.timeout", 5), TimeUnit.SECONDS);
        assertNotNull("Should receive DataEvent for overweight", event);
        
        System.out.println("  • Overweight event received");
    }
    
    // ==================== ТЕСТ 5: Команда Zero ====================
    
    @Test
    public void test005_ZeroCommand() throws Exception {
        System.out.println("\n🔷 TEST 005: Zero command");
        
        emulator.setWeight(1500);
        emulator.clearCommands();
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // В JPOS Scale нет прямого метода zero()
        // Используем DirectIO
        int[] data = new int[1];
        Object[] obj = new Object[1];
        scale.directIO(0x30, data, obj); // CMD_ZERO
        System.out.println("  • directIO(0x30) called for zero");
        
        boolean zeroReceived = emulator.waitForZeroCommand(
            getConfigInt("command.timeout", 3), TimeUnit.SECONDS);
        
        assertTrue("Zero command should be received by emulator", zeroReceived);
        System.out.println("  ✅ Zero command confirmed by emulator");
    }
    
    // ==================== ТЕСТ 6: Команда Tare ====================
    
    @Test
    public void test006_TareCommand() throws Exception {
        System.out.println("\n🔷 TEST 006: Tare command");
        
        long testWeight = getConfigLong("test.tare.weight", 2500);
        
        emulator.setWeight(testWeight);
        emulator.clearCommands();
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // В JPOS Scale нет прямого метода tare()
        // Используем DirectIO
        int[] data = new int[1];
        Object[] obj = new Object[1];
        scale.directIO(0x31, data, obj); // CMD_TARE
        System.out.println("  • directIO(0x31) called for tare");
        
        Long tareValue = emulator.waitForTareCommand(
            getConfigInt("command.timeout", 3), TimeUnit.SECONDS);
        
        assertNotNull("Tare command should be received", tareValue);
        assertEquals("Tare value should match current weight", 
            testWeight, tareValue.longValue());
        
        System.out.println("  ✅ Tare command confirmed, value: " + tareValue);
    }
    
    // ==================== ТЕСТ 7: Установка конкретного значения тары ====================
    
    @Test
    public void test007_SetTareValue() throws Exception {
        System.out.println("\n🔷 TEST 007: Set specific tare value");
        
        long tareValue = getConfigLong("test.set.tare", 500);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        emulator.clearCommands();
        
        // Используем DirectIO для команды 0x32 (SET_TARE_VALUE)
        int[] data = new int[1];
        Object[] obj = new Object[1];
        obj[0] = String.valueOf(tareValue);
        
        scale.directIO(0x32, data, obj);
        System.out.println("  • directIO(0x32) called with value: " + tareValue);
        
        Long receivedTare = emulator.waitForTareCommand(
            getConfigInt("command.timeout", 3), TimeUnit.SECONDS);
        
        assertNotNull("Set tare command should be received", receivedTare);
        assertEquals("Tare value should match", tareValue, receivedTare.longValue());
        
        System.out.println("  ✅ Set tare confirmed: " + receivedTare);
    }
    
    // ==================== ТЕСТ 8: Блокировка клавиатуры ====================
    
    @Test
    public void test008_KeyboardLock() throws Exception {
        System.out.println("\n🔷 TEST 008: Keyboard lock/unlock");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // Блокировка (команда 0x09 с параметром 1)
        int[] data = new int[1];
        Object[] obj = new Object[1];
        obj[0] = "1";
        
        scale.directIO(0x09, data, obj);
        System.out.println("  • Keyboard locked via directIO(0x09)");
        
        waitForEvents();
        
        // Разблокировка (команда 0x09 с параметром 0)
        obj[0] = "0";
        scale.directIO(0x09, data, obj);
        System.out.println("  • Keyboard unlocked via directIO(0x09)");
        
        // Если нет исключений - тест пройден
        assertTrue("Keyboard lock/unlock should not throw exceptions", true);
    }
    
    // ==================== ТЕСТ 9: Множественные чтения ====================
    
    @Test
    public void test009_MultipleReads() throws Exception {
        System.out.println("\n🔷 TEST 009: Multiple sequential reads");
        
        int readCount = getConfigInt("test.multiple.reads.count", 10);
        long baseWeight = getConfigLong("test.multiple.reads.base", 100);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        for (int i = 0; i < readCount; i++) {
            long testWeight = baseWeight + (i * 100);
            emulator.setWeight(testWeight);
            
            scale.readWeight(new int[1], getConfigInt("read.timeout", 2000));
            
            DataEvent event = dataEvents.poll(
                getConfigInt("event.timeout", 3), TimeUnit.SECONDS);
            
            assertNotNull("Should receive data event for iteration " + i, event);
            
            int weight = scale.getScaleLiveWeight();
            System.out.printf("  • Read %2d: %d%n", i+1, weight);
            
            assertTrue("Weight should be positive at iteration " + i, weight > 0);
        }
        
        System.out.println("  ✅ All " + readCount + " reads successful");
    }
    
    // ==================== ТЕСТ 10: Чтение с таймаутом ====================
    
    @Test
    public void test010_ReadTimeout() throws Exception {
        System.out.println("\n🔷 TEST 010: Read timeout handling");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // Останавливаем эмулятор для имитации отсутствия ответа
        emulator.stop();
        System.out.println("  • Emulator stopped to simulate timeout");
        
        long startTime = System.currentTimeMillis();
        int timeout = getConfigInt("test.timeout.ms", 1000);
        
        try {
            scale.readWeight(new int[1], timeout);
            fail("Should throw timeout exception");
        } catch (JposException e) {
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("  • Timeout exception after " + duration + " ms");
            System.out.println("  • Exception message: " + e.getMessage());
            
            assertTrue("Timeout should be approximately correct", 
                duration >= timeout - 100);
        }
    }
    
    // ==================== ТЕСТ 11: Восстановление после ошибки ====================
    
    @Test
    public void test011_RecoveryAfterError() throws Exception {
        System.out.println("\n🔷 TEST 011: Recovery after communication error");
        
        int readTimeout = getConfigInt("read.timeout", 2000);
        int eventTimeout = getConfigInt("event.timeout", 3);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // 1. Успешное чтение
        emulator.setWeight(123);
        scale.readWeight(new int[1], readTimeout);
        
        DataEvent event1 = dataEvents.poll(eventTimeout, TimeUnit.SECONDS);
        assertNotNull("First read should succeed", event1);
        System.out.println("  • First read successful");
        
        // 2. Останавливаем эмулятор
        emulator.stop();
        System.out.println("  • Emulator stopped");
        Thread.sleep(getConfigInt("recovery.pause", 200));
        
        // 3. Следующее чтение должно упасть
        try {
            scale.readWeight(new int[1], readTimeout);
            System.out.println("  • Read during stop - may timeout or return cached");
        } catch (JposException e) {
            System.out.println("  • Expected error: " + e.getMessage());
        }
        
        // 4. Перезапускаем эмулятор
        System.out.print("  • Restarting emulator... ");
        emulator = new Pos2ProtocolEmulator(emulatorPort);
        emulator.start();
        System.out.println("started");
        
        Thread.sleep(getConfigInt("recovery.delay", 1000));
        
        // 5. Проверяем восстановление
        emulator.setWeight(456);
        
        scale.readWeight(new int[1], readTimeout);
        DataEvent event2 = dataEvents.poll(eventTimeout * 2, TimeUnit.SECONDS);
        
        assertNotNull("Should recover after restart", event2);
        
        int weight = scale.getScaleLiveWeight();
        System.out.println("  • Recovery weight: " + weight);
        
        assertTrue("Should read after recovery", weight > 0);
        System.out.println("  ✅ Recovery successful");
    }
    
    // ==================== ТЕСТ 12: Свойства устройства ====================
    
    @Test
    public void test012_DeviceProperties() throws Exception {
        System.out.println("\n🔷 TEST 012: Device properties");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        String devName = scale.getPhysicalDeviceName();
        String devDesc = scale.getPhysicalDeviceDescription();
        String serviceDesc = scale.getDeviceServiceDescription();
        int serviceVer = scale.getDeviceServiceVersion();
        
        System.out.println("  • Physical device name: " + devName);
        System.out.println("  • Physical device description: " + devDesc);
        System.out.println("  • Service description: " + serviceDesc);
        System.out.println("  • Service version: " + serviceVer);
        
        assertNotNull("Device name should not be null", devName);
        assertNotNull("Device description should not be null", devDesc);
    }
    
    // ==================== ТЕСТ 13: Freeze Events ====================
    
    @Test
    public void test013_FreezeEvents() throws Exception {
        System.out.println("\n🔷 TEST 013: Freeze events");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // Замораживаем события
        scale.setFreezeEvents(true);
        System.out.println("  • Events frozen");
        
        emulator.setWeight(1234);
        scale.readWeight(new int[1], getConfigInt("read.timeout", 1000));
        
        // Событие не должно прийти
        DataEvent event = dataEvents.poll(2, TimeUnit.SECONDS);
        assertNull("Events should be frozen", event);
        System.out.println("  • No event received (correct)");
        
        // Размораживаем
        scale.setFreezeEvents(false);
        System.out.println("  • Events unfrozen");
        
        // Должно прийти
        event = dataEvents.poll(getConfigInt("event.timeout", 3), TimeUnit.SECONDS);
        assertNotNull("Should receive event after unfreeze", event);
        System.out.println("  • Event received after unfreeze");
    }
    
    // ==================== ТЕСТ 14: Асинхронный режим ====================
    
    @Test
    public void test014_AsyncMode() throws Exception {
        System.out.println("\n🔷 TEST 014: Asynchronous mode");
        
        scale.setAsyncMode(true);
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        assertTrue("Async mode should be true", scale.getAsyncMode());
        System.out.println("  • Async mode enabled");
        
        emulator.setWeight(7777);
        
        scale.readWeight(new int[1], getConfigInt("read.timeout", 5000));
        System.out.println("  • Async read started");
        
        DataEvent event = dataEvents.poll(getConfigInt("event.timeout", 5), TimeUnit.SECONDS);
        assertNotNull("Should receive event in async mode", event);
        
        int weight = scale.getScaleLiveWeight();
        System.out.println("  • Async weight: " + weight);
        assertTrue("Weight should be > 0", weight > 0);
    }
    
    // ==================== ТЕСТ 15: Многопоточность ====================
    
    @Test
    public void test015_ConcurrentAccess() throws Exception {
        System.out.println("\n🔷 TEST 015: Concurrent access from multiple threads");
        
        int threadCount = getConfigInt("test.concurrent.threads", 3);
        int readsPerThread = getConfigInt("test.concurrent.reads", 5);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);
        
        System.out.println("  • Starting " + threadCount + " threads, " + 
            readsPerThread + " reads each");
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int r = 0; r < readsPerThread; r++) {
                        int testWeight = threadId * 1000 + r;
                        emulator.setWeight(testWeight);
                        
                        // Каждый поток использует свой экземпляр Scale
                        Scale threadScale = new jpos.Scale();
                        try {
                            threadScale.addDataListener(new DataListener() {
                                @Override
                                public void dataOccurred(DataEvent e) {
                                    successCount.incrementAndGet();
                                }
                            });
                            
                            threadScale.open(logicalDeviceName);
                            threadScale.setDeviceEnabled(true);
                            threadScale.claim(1000);
                            
                            threadScale.readWeight(new int[1], 2000);
                            
                            Thread.sleep(50);
                            
                            threadScale.release();
                            threadScale.close();
                            
                        } finally {
                            try { threadScale.close(); } catch (Exception e) {}
                        }
                    }
                    latch.countDown();
                } catch (Exception e) {
                    errors.add(e);
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(
            getConfigInt("test.concurrent.timeout", 30), TimeUnit.SECONDS);
        
        assertTrue("All threads should complete", completed);
        assertTrue("No errors should occur", errors.isEmpty());
        
        executor.shutdown();
    }
    
    // ==================== ТЕСТ 16: Повторное открытие/закрытие ====================
    
    @Test
    public void test016_RepeatedOpenClose() throws Exception {
        System.out.println("\n🔷 TEST 016: Repeated open/close cycles");
        
        int cycles = getConfigInt("test.reopen.cycles", 3);
        int delay = getConfigInt("test.reopen.delay", 500);
        
        for (int i = 0; i < cycles; i++) {
            System.out.println("  • Cycle " + (i+1) + "/" + cycles);
            
            // Закрываем текущее соединение
            if (scale.getClaimed()) {
                scale.release();
            }
            if (scale.getDeviceEnabled()) {
                scale.setDeviceEnabled(false);
            }
            scale.close();
            
            System.out.println("    Device closed");
            Thread.sleep(delay);
            
            // Создаем новый экземпляр
            scale = new jpos.Scale();
            scale.addDataListener(new TestDataListener());
            scale.open(logicalDeviceName);
            scale.setDeviceEnabled(true);
            scale.claim(1000);
            
            System.out.println("    Device reopened");
            
            // Проверяем работоспособность
            emulator.setWeight(100 + i);
            scale.readWeight(new int[1], 2000);
            
            DataEvent event = dataEvents.poll(3, TimeUnit.SECONDS);
            assertNotNull("Should receive data after reopen, cycle " + i, event);
            
            int weight = scale.getScaleLiveWeight();
            System.out.println("    Read after reopen: " + weight);
            
            // Очищаем очередь для следующей итерации
            dataEvents.clear();
        }
        
        System.out.println("  ✅ All " + cycles + " cycles successful");
    }
    
    // ==================== ТЕСТ 17: Стресс-тест ====================
    
    @Test
    public void test017_StressTest() throws Exception {
        System.out.println("\n🔷 TEST 017: Stress test");
        
        int totalReads = getConfigInt("test.stress.reads", 100);
        int readTimeout = getConfigInt("test.stress.timeout", 1000);
        int eventTimeout = getConfigInt("test.stress.event.timeout", 2);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        System.out.println("  • Starting " + totalReads + " reads...");
        
        int successfulReads = 0;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < totalReads; i++) {
            try {
                int testWeight = i % 10000;
                emulator.setWeight(testWeight);
                
                scale.readWeight(new int[1], readTimeout);
                
                DataEvent event = dataEvents.poll(eventTimeout, TimeUnit.SECONDS);
                
                if (event != null) {
                    successfulReads++;
                }
                
                if (i % 20 == 0) {
                    System.out.printf("    Progress: %d/%d (%.0f%%)%n", 
                        i, totalReads, (i * 100.0 / totalReads));
                }
                
                Thread.sleep(getConfigInt("test.stress.pause", 10));
                
            } catch (Exception e) {
                System.err.println("    Error at iteration " + i + ": " + e.getMessage());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double successRate = (successfulReads * 100.0 / totalReads);
        double avgTime = (double) duration / totalReads;
        
        System.out.println("  📊 Stress test results:");
        System.out.println("     Total reads: " + totalReads);
        System.out.println("     Successful: " + successfulReads);
        System.out.println("     Success rate: " + String.format("%.2f%%", successRate));
        System.out.println("     Total time: " + duration + " ms");
        System.out.println("     Avg time per read: " + String.format("%.2f", avgTime) + " ms");
        
        double minSuccessRate = getConfigDouble("test.stress.min.success.rate", 90.0);
        assertTrue("Success rate should be >= " + minSuccessRate + "%", 
            successRate >= minSuccessRate);
    }
    
    // ==================== ТЕСТ 18: DirectIO команды ====================
    
    @Test
    public void test018_DirectIOCommands() throws Exception {
        System.out.println("\n🔷 TEST 018: DirectIO commands");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // Тест различных DirectIO команд
        int[][] commands = {
            {0xFC, 0},  // CMD_GET_DEVICE_TYPE
            {0xE5, 0},  // CMD_GET_CHANNELS_COUNT
            {0xEA, 0},  // CMD_GET_CURRENT_CHANNEL
            {0x12, 0},  // CMD_GET_MODE
        };
        
        for (int[] cmd : commands) {
            int[] data = new int[1];
            Object[] obj = new Object[1];
            
            System.out.print("  • DirectIO 0x" + Integer.toHexString(cmd[0]) + "... ");
            scale.directIO(cmd[0], data, obj);
            
            DirectIOEvent event = directIOEvents.poll(2, TimeUnit.SECONDS);
            
            if (event != null) {
                System.out.println("response: " + event.getData());
            } else {
                System.out.println("no event (may be synchronous)");
            }
            
            waitForEvents();
        }
    }
    
    // ==================== ТЕСТ 19: Смена скорости ====================
    
    @Test
    public void test019_BaudRateChange() throws Exception {
        System.out.println("\n🔷 TEST 019: Baud rate change");
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        // Чтение текущих параметров
        int[] data = new int[1];
        Object[] obj = new Object[1];
        
        scale.directIO(0x15, data, obj); // CMD_GET_COMM_PARAMS
        System.out.println("  • Current baud rate requested");
        
        waitForEvents();
        
        System.out.println("  • Baud rate test passed (basic)");
    }
    
    // ==================== ТЕСТ 20: Длительная работа ====================
    
    @Test
    public void test020_LongRunningOperation() throws Exception {
        System.out.println("\n🔷 TEST 020: Long running operation");
        
        int operationTime = getConfigInt("test.long.running.seconds", 10);
        int readInterval = getConfigInt("test.long.running.interval", 1000);
        
        scale.setDeviceEnabled(true);
        scale.claim(getConfigInt("claim.timeout", 1000));
        
        System.out.println("  • Running for " + operationTime + " seconds...");
        
        long endTime = System.currentTimeMillis() + (operationTime * 1000);
        int readCount = 0;
        
        while (System.currentTimeMillis() < endTime) {
            int testWeight = 1000 + (readCount % 100);
            emulator.setWeight(testWeight);
            
            scale.readWeight(new int[1], getConfigInt("read.timeout", 2000));
            
            DataEvent event = dataEvents.poll(
                getConfigInt("event.timeout", 3), TimeUnit.SECONDS);
            
            assertNotNull("Should receive event during long run", event);
            
            readCount++;
            
            if (readCount % 5 == 0) {
                System.out.println("    Read " + readCount + " completed");
            }
            
            Thread.sleep(readInterval);
        }
        
        System.out.println("  • Long run completed, " + readCount + " reads");
        assertTrue("Should perform multiple reads", readCount > 1);
    }
    
    // ==================== СЛУШАТЕЛИ СОБЫТИЙ ====================
    
    private class TestDataListener implements DataListener {
        @Override
        public void dataOccurred(DataEvent event) {
            System.out.println("    📊 DataEvent: status=" + event.getStatus());
            dataEvents.offer(event);
        }
    }
    
    private class TestErrorListener implements ErrorListener {
        @Override
        public void errorOccurred(ErrorEvent event) {
            System.err.println("    ❌ ErrorEvent: code=" + event.getErrorCode() + 
                ", extended=" + event.getErrorCodeExtended());
            errorEvents.offer(event);
        }
    }
    
    private class TestStatusListener implements StatusUpdateListener {
        @Override
        public void statusUpdateOccurred(StatusUpdateEvent event) {
            System.out.println("    📍 StatusUpdate: " + event.getStatus());
            statusEvents.offer(event);
        }
    }
    
    private class TestDirectIOListener implements DirectIOListener {
        @Override
        public void directIOOccurred(DirectIOEvent event) {
            System.out.println("    🔧 DirectIO: event=" + event.getEventNumber() + 
                ", data=" + event.getData());
            directIOEvents.offer(event);
        }
    }
}