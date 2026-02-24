package com.bsmart.jpos.scale;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Эмулятор весов с полной поддержкой протокола POS2 V1.3
 * Работает через gnu.io.SerialPort (RXTX)
 */
public class Pos2ProtocolEmulator implements SerialPortEventListener {
    
    // Константы протокола
    public static final byte STX = 0x02;
    public static final byte ENQ = 0x05;
    public static final byte ACK = 0x06;
    public static final byte NAK = 0x15;
    
    // Коды команд (по спецификации)
    public static final byte CMD_SET_MODE = 0x07;
    public static final byte CMD_KEY_EMULATION = 0x08;
    public static final byte CMD_KEYBOARD_LOCK = 0x09;
    public static final byte CMD_GET_STATUS = 0x0A;
    public static final byte CMD_GET_MODE = 0x12;
    public static final byte CMD_SET_COMM_PARAMS = 0x14;
    public static final byte CMD_GET_COMM_PARAMS = 0x15;
    public static final byte CMD_CHANGE_PASSWORD = 0x16;
    public static final byte CMD_OPEN_CASH_DRAWER = 0x28;
    public static final byte CMD_SET_ZERO = 0x30;
    public static final byte CMD_SET_TARE = 0x31;
    public static final byte CMD_SET_TARE_VALUE = 0x32;
    public static final byte CMD_SET_GOODS = 0x33;
    public static final byte CMD_GET_SCALE_STATUS = 0x3A;
    public static final byte CMD_GET_SCALE_STATUS_EXT = 0x3B;
    public static final byte CMD_WRITE_CALIB_POINT = 0x70;
    public static final byte CMD_READ_CALIB_POINT = 0x71;
    public static final byte CMD_START_CALIB = 0x72;
    public static final byte CMD_GET_CALIB_STATUS = 0x73;
    public static final byte CMD_ABORT_CALIB = 0x74;
    public static final byte CMD_GET_ADC = 0x75;
    public static final byte CMD_GET_KEYBOARD = (byte)0x90;
    public static final byte CMD_GET_CHANNELS_COUNT = (byte)0xE5;
    public static final byte CMD_SELECT_CHANNEL = (byte)0xE6;
    public static final byte CMD_SET_CHANNEL_STATE = (byte)0xE7;
    public static final byte CMD_GET_CHANNEL_PARAMS = (byte)0xE8;
    public static final byte CMD_SET_CHANNEL_PARAMS = (byte)0xE9;
    public static final byte CMD_GET_CURRENT_CHANNEL = (byte)0xEA;
    public static final byte CMD_RESET_CHANNEL = (byte)0xEF;
    public static final byte CMD_RESET = (byte)0xF0;
    public static final byte CMD_GET_DEVICE_TYPE = (byte)0xFC;
    
    // Коды ошибок
    public static final byte ERR_SUCCESS = 0x00;
    public static final byte ERR_TARE_VALUE = 0x17;
    public static final byte ERR_UNKNOWN_COMMAND = 0x78;
    public static final byte ERR_INVALID_DATA_LENGTH = 0x79;
    public static final byte ERR_INVALID_PASSWORD = 0x7A;
    public static final byte ERR_COMMAND_NOT_AVAILABLE = 0x7B;
    public static final byte ERR_INVALID_PARAM = 0x7C;
    public static final byte ERR_SET_ZERO_FAILED = (byte)0x96;
    public static final byte ERR_SET_TARE_FAILED = (byte)0x97;
    public static final byte ERR_WEIGHT_UNSTABLE = (byte)0x98;
    public static final byte ERR_EEPROM_FAILED = (byte)0xA6;
    public static final byte ERR_INTERFACE_NOT_SUPPORTED = (byte)0xA7;
    public static final byte ERR_PASSWORD_LIMIT = (byte)0xAA;
    public static final byte ERR_CALIB_LOCKED = (byte)0xB4;
    public static final byte ERR_KEYBOARD_LOCKED = (byte)0xB5;
    public static final byte ERR_CANNOT_CHANGE_CHANNEL_TYPE = (byte)0xB6;
    public static final byte ERR_CANNOT_DISABLE_CHANNEL = (byte)0xB7;
    public static final byte ERR_CHANNEL_OPERATION_NOT_ALLOWED = (byte)0xB8;
    public static final byte ERR_INVALID_CHANNEL = (byte)0xB9;
    public static final byte ERR_ADC_NO_RESPONSE = (byte)0xBA;
    
    // Режимы
    public static final byte MODE_NORMAL = 0x00;
    public static final byte MODE_CALIBRATION = 0x01;
    
    // Коды клавиш
    public static final byte KEY_AUTOZERO = 0x12;
    public static final byte KEY_TARE = 0x13;
    
    // Коды скоростей
    public static final Map<Integer, Integer> BAUDRATE_VALUES = new HashMap<>();
    static {
        BAUDRATE_VALUES.put(2400, 0x00);
        BAUDRATE_VALUES.put(4800, 0x01);
        BAUDRATE_VALUES.put(9600, 0x02);
        BAUDRATE_VALUES.put(19200, 0x03);
        BAUDRATE_VALUES.put(38400, 0x04);
        BAUDRATE_VALUES.put(57600, 0x05);
        BAUDRATE_VALUES.put(115200, 0x06);
    }
    
    // Параметры порта
    private final String portName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    private CommPortIdentifier portIdentifier;
    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    // Буфер для чтения данных
    private final byte[] readBuffer = new byte[1024];
    private int bufferPos = 0;
    private boolean waitingForEnqResponse = false;
    
    // Состояние весов
    private byte currentMode = MODE_NORMAL;
    private byte[] adminPassword = new byte[]{'0','0','0','0'};
    private boolean keyboardLocked = false;
    private int requestCount = 0;
    private int errorCount = 0;
    
    // Параметры веса
    private long currentWeight = 0;
    private long currentTare = 0;
    private boolean weightStable = true;
    private boolean weightOverweight = false;
    private int selectedChannel = 0;
    
    // Параметры канала
    private ChannelParams channelParams = new ChannelParams();
    
    // Очередь команд от драйвера
    private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
    
    /**
     * Конструктор эмулятора
     * @param portName имя порта (например, "COM5" в Windows или "/dev/ttyS5" в Linux)
     */
    public Pos2ProtocolEmulator(String portName) {
        this.portName = portName;
        initDefaultChannelParams();
    }
    
    private void initDefaultChannelParams() {
        channelParams.flags = 0x0001;
        channelParams.decimalPoint = 3;
        channelParams.power = -3;
        channelParams.npv = 30000;
        channelParams.nmpv = 20;
        channelParams.maxTare = 15000;
        channelParams.range1 = 15000;
        channelParams.range2 = 30000;
        channelParams.range3 = 0;
        channelParams.discr1 = 1;
        channelParams.discr2 = 2;
        channelParams.discr3 = 5;
        channelParams.discr4 = 10;
        channelParams.calibPoints = 3;
    }
    
    // ========== Методы управления для тестов ==========
    
    public void setWeight(long weightGrams) {
        this.currentWeight = weightGrams;
    }
    
    public void setStable(boolean stable) {
        this.weightStable = stable;
    }
    
    public void setOverweight(boolean overweight) {
        this.weightOverweight = overweight;
    }
    
    public void setTare(long tareGrams) {
        this.currentTare = tareGrams;
    }
    
    public int getRequestCount() {
        return requestCount;
    }
    
    public int getErrorCount() {
        return errorCount;
    }
    
    public Long waitForTareCommand(long timeout, TimeUnit unit) throws InterruptedException {
        Command cmd = commandQueue.poll(timeout, unit);
        return cmd != null && cmd.type == Command.Type.TARE ? cmd.value : null;
    }
    
    public boolean waitForZeroCommand(long timeout, TimeUnit unit) throws InterruptedException {
        Command cmd = commandQueue.poll(timeout, unit);
        return cmd != null && cmd.type == Command.Type.ZERO;
    }
    
    public void clearCommands() {
        commandQueue.clear();
    }
    
    // ========== Запуск/остановка эмулятора ==========
    
    public void start() throws Exception {
        if (running.get()) return;
        
        try {
            // Получаем идентификатор порта
            portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
            
            if (portIdentifier.isCurrentlyOwned()) {
                throw new Exception("Port " + portName + " is currently in use");
            }
            
            // Открываем порт
            serialPort = (SerialPort) portIdentifier.open("ScaleEmulator", 2000);
            
            // Настраиваем параметры
            serialPort.setSerialPortParams(
                9600,                       // скорость
                SerialPort.DATABITS_8,       // 8 бит данных
                SerialPort.STOPBITS_1,       // 1 стоп-бит
                SerialPort.PARITY_NONE       // без четности
            );
            
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            serialPort.enableReceiveTimeout(100); // таймаут чтения 100 мс
            
            // Получаем потоки
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            
            // Добавляем слушателя событий
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
            
            connected.set(true);
            running.set(true);
            
            System.out.println("Pos2ProtocolEmulator started on " + portName);
            
        } catch (Exception e) {
            throw new Exception("Failed to start emulator on " + portName + ": " + e.getMessage(), e);
        }
    }
    
    public void stop() {
        running.set(false);
        connected.set(false);
        
        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
        }
        
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            // ignore
        }
        
        System.out.println("Pos2ProtocolEmulator stopped");
    }
    
    // ========== Обработка событий порта ==========
    
    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            case SerialPortEvent.DATA_AVAILABLE:
                handleDataAvailable();
                break;
            case SerialPortEvent.BI:
            case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                // Игнорируем другие события
                break;
        }
    }
    
    private void handleDataAvailable() {
        try {
            while (inputStream.available() > 0 && running.get()) {
                int b = inputStream.read();
                if (b < 0) break;
                
                processByte((byte)b);
            }
        } catch (IOException e) {
            errorCount++;
            System.err.println("Error reading from port: " + e.getMessage());
        }
    }
    
    // ========== Обработка протокола ==========
    
    private void processByte(byte b) {
        if (!waitingForEnqResponse && bufferPos == 0 && b != STX && b != ENQ) {
            // Игнорируем мусор
            return;
        }
        
        if (b == ENQ) {
            handleEnq();
            return;
        }
        
        if (b == STX) {
            // Начинаем новое сообщение
            bufferPos = 0;
            readBuffer[bufferPos++] = b;
            return;
        }
        
        if (bufferPos > 0) {
            readBuffer[bufferPos++] = b;
            
            // Проверяем, получили ли всю длину
            if (bufferPos == 2) {
                // Получили байт длины
                // Ждем остальных байт
            }
            
            // Проверяем, получили ли полное сообщение
            if (bufferPos >= 2) {
                int length = readBuffer[1] & 0xFF;
                int totalLength = 2 + length + 1; // STX + длина + данные + LRC
                
                if (bufferPos == totalLength) {
                    // Получили полное сообщение
                    processMessage();
                    bufferPos = 0;
                }
            }
        }
    }
    
    private void handleEnq() {
        requestCount++;
        waitingForEnqResponse = true;
        
        try {
            // По спецификации: на ENQ отвечаем ACK
            outputStream.write(ACK);
            outputStream.flush();
            waitingForEnqResponse = false;
        } catch (IOException e) {
            errorCount++;
        }
    }
    
    private void processMessage() {
        requestCount++;
        
        byte stx = readBuffer[0];
        byte length = readBuffer[1];
        byte[] data = new byte[length];
        System.arraycopy(readBuffer, 2, data, 0, length);
        byte receivedLrc = readBuffer[2 + length];
        
        // Проверяем контрольную сумму
        byte calculatedLrc = calculateLRC(stx, length, data);
        if (calculatedLrc != receivedLrc) {
            // Ошибка контрольной суммы
            sendNAK();
            errorCount++;
            return;
        }
        
        // Подтверждаем прием
        sendACK();
        
        // Обрабатываем команду
        byte command = data[0];
        byte[] parameters = new byte[length - 1];
        if (parameters.length > 0) {
            System.arraycopy(data, 1, parameters, 0, parameters.length);
        }
        
        processCommand(command, parameters);
    }
    
    private void sendACK() {
        try {
            outputStream.write(ACK);
            outputStream.flush();
        } catch (IOException e) {
            errorCount++;
        }
    }
    
    private void sendNAK() {
        try {
            outputStream.write(NAK);
            outputStream.flush();
        } catch (IOException e) {
            errorCount++;
        }
    }
    
    private byte calculateLRC(byte stx, byte length, byte[] data) {
        byte lrc = stx;
        lrc ^= length;
        for (byte b : data) {
            lrc ^= b;
        }
        return lrc;
    }
    
    private void processCommand(byte command, byte[] parameters) {
        System.out.println("Processing command: 0x" + Integer.toHexString(command & 0xFF) + 
                         ", params length: " + parameters.length);
        
        try {
            switch (command) {
                case CMD_SET_MODE:
                    handleSetMode(parameters);
                    break;
                case CMD_KEY_EMULATION:
                    handleKeyEmulation(parameters);
                    break;
                case CMD_KEYBOARD_LOCK:
                    handleKeyboardLock(parameters);
                    break;
                case CMD_GET_STATUS:
                    handleGetStatus();
                    break;
                case CMD_GET_MODE:
                    handleGetMode();
                    break;
                case CMD_SET_COMM_PARAMS:
                    handleSetCommParams(parameters);
                    break;
                case CMD_GET_COMM_PARAMS:
                    handleGetCommParams();
                    break;
                case CMD_CHANGE_PASSWORD:
                    handleChangePassword(parameters);
                    break;
                case CMD_OPEN_CASH_DRAWER:
                    handleOpenCashDrawer(parameters);
                    break;
                case CMD_SET_ZERO:
                    handleSetZero(parameters);
                    break;
                case CMD_SET_TARE:
                    handleSetTare(parameters);
                    break;
                case CMD_SET_TARE_VALUE:
                    handleSetTareValue(parameters);
                    break;
                case CMD_SET_GOODS:
                    handleSetGoods(parameters);
                    break;
                case CMD_GET_SCALE_STATUS:
                    handleGetScaleStatus();
                    break;
                case CMD_GET_SCALE_STATUS_EXT:
                    handleGetScaleStatusExt();
                    break;
                case CMD_WRITE_CALIB_POINT:
                    handleWriteCalibPoint(parameters);
                    break;
                case CMD_READ_CALIB_POINT:
                    handleReadCalibPoint(parameters);
                    break;
                case CMD_START_CALIB:
                    handleStartCalib(parameters);
                    break;
                case CMD_GET_CALIB_STATUS:
                    handleGetCalibStatus(parameters);
                    break;
                case CMD_ABORT_CALIB:
                    handleAbortCalib(parameters);
                    break;
                case CMD_GET_ADC:
                    handleGetAdc(parameters);
                    break;
                case CMD_GET_KEYBOARD:
                    handleGetKeyboard(parameters);
                    break;
                case CMD_GET_CHANNELS_COUNT:
                    handleGetChannelsCount();
                    break;
                case CMD_SELECT_CHANNEL:
                    handleSelectChannel(parameters);
                    break;
                case CMD_SET_CHANNEL_STATE:
                    handleSetChannelState(parameters);
                    break;
                case CMD_GET_CHANNEL_PARAMS:
                    handleGetChannelParams(parameters);
                    break;
                case CMD_SET_CHANNEL_PARAMS:
                    handleSetChannelParams(parameters);
                    break;
                case CMD_GET_CURRENT_CHANNEL:
                    handleGetCurrentChannel();
                    break;
                case CMD_RESET_CHANNEL:
                    handleResetChannel(parameters);
                    break;
                case CMD_RESET:
                    handleReset(parameters);
                    break;
                case CMD_GET_DEVICE_TYPE:
                    handleGetDeviceType();
                    break;
                default:
                    sendErrorResponse(command, ERR_UNKNOWN_COMMAND);
            }
        } catch (Exception e) {
            errorCount++;
            sendErrorResponse(command, ERR_COMMAND_NOT_AVAILABLE);
        }
    }
    
    // ========== Реализация команд (такая же как в предыдущей версии) ==========
    
    private void handleSetMode(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_MODE, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 5) {
            sendErrorResponse(CMD_SET_MODE, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte mode = params[4];
        if (mode != MODE_NORMAL && mode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_SET_MODE, ERR_INVALID_PARAM);
            return;
        }
        
        currentMode = mode;
        sendSuccessResponse(CMD_SET_MODE);
    }
    
    private void handleKeyEmulation(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_KEY_EMULATION, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 5) {
            sendErrorResponse(CMD_KEY_EMULATION, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte keyCode = params[4];
        System.out.println("Key emulation: 0x" + Integer.toHexString(keyCode & 0xFF));
        
        if (keyCode == KEY_AUTOZERO) {
            commandQueue.offer(new Command(Command.Type.ZERO, 0));
        } else if (keyCode == KEY_TARE) {
            commandQueue.offer(new Command(Command.Type.TARE, currentTare));
        }
        
        sendSuccessResponse(CMD_KEY_EMULATION);
    }
    
    private void handleKeyboardLock(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_KEYBOARD_LOCK, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 5) {
            sendErrorResponse(CMD_KEYBOARD_LOCK, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte value = params[4];
        if (value == 0) {
            keyboardLocked = false;
        } else if (value == 1) {
            keyboardLocked = true;
        } else {
            sendErrorResponse(CMD_KEYBOARD_LOCK, ERR_INVALID_PARAM);
            return;
        }
        
        sendSuccessResponse(CMD_KEYBOARD_LOCK);
    }
    
    private void handleGetStatus() {
        byte[] response = new byte[3];
        response[0] = CMD_GET_STATUS;
        response[1] = ERR_SUCCESS;
        
        byte status = 0;
        if (weightStable) status |= 0x01;
        if (weightOverweight) status |= 0x02;
        response[2] = status;
        
        sendResponse(response);
    }
    
    private void handleGetMode() {
        byte[] response = new byte[3];
        response[0] = CMD_GET_MODE;
        response[1] = ERR_SUCCESS;
        response[2] = currentMode;
        
        sendResponse(response);
    }
    
    private void handleSetCommParams(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_COMM_PARAMS, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 6) {
            sendErrorResponse(CMD_SET_COMM_PARAMS, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte baudCode = params[4];
        byte timeout = params[5];
        
        System.out.println("Set comm params: baudCode=" + baudCode + ", timeout=" + timeout);
        sendSuccessResponse(CMD_SET_COMM_PARAMS);
    }
    
    private void handleGetCommParams() {
        byte[] response = new byte[4];
        response[0] = CMD_GET_COMM_PARAMS;
        response[1] = ERR_SUCCESS;
        response[2] = 0x02; // 9600
        response[3] = 100;   // 100 мс
        
        sendResponse(response);
    }
    
    private void handleChangePassword(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_CHANGE_PASSWORD, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 8) {
            sendErrorResponse(CMD_CHANGE_PASSWORD, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        adminPassword = Arrays.copyOfRange(params, 4, 8);
        System.out.println("Password changed to: " + new String(adminPassword));
        
        sendSuccessResponse(CMD_CHANGE_PASSWORD);
    }
    
    private void handleOpenCashDrawer(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_OPEN_CASH_DRAWER, ERR_INVALID_PASSWORD);
            return;
        }
        
        System.out.println("Cash drawer opened");
        sendSuccessResponse(CMD_OPEN_CASH_DRAWER);
    }
    
    private void handleSetZero(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_ZERO, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (!weightStable) {
            sendErrorResponse(CMD_SET_ZERO, ERR_WEIGHT_UNSTABLE);
            return;
        }
        
        currentWeight = 0;
        commandQueue.offer(new Command(Command.Type.ZERO, 0));
        System.out.println("Zero set");
        
        sendSuccessResponse(CMD_SET_ZERO);
    }
    
    private void handleSetTare(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_TARE, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (!weightStable) {
            sendErrorResponse(CMD_SET_TARE, ERR_WEIGHT_UNSTABLE);
            return;
        }
        
        currentTare = currentWeight;
        commandQueue.offer(new Command(Command.Type.TARE, currentTare));
        System.out.println("Tare set to: " + currentTare);
        
        sendSuccessResponse(CMD_SET_TARE);
    }
    
    private void handleSetTareValue(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_TARE_VALUE, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 6) {
            sendErrorResponse(CMD_SET_TARE_VALUE, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        int tareValue = (params[4] & 0xFF) | ((params[5] & 0xFF) << 8);
        if (tareValue < 0 || tareValue > channelParams.maxTare) {
            sendErrorResponse(CMD_SET_TARE_VALUE, ERR_TARE_VALUE);
            return;
        }
        
        currentTare = tareValue;
        commandQueue.offer(new Command(Command.Type.TARE, currentTare));
        System.out.println("Tare value set to: " + currentTare);
        
        sendSuccessResponse(CMD_SET_TARE_VALUE);
    }
    
    private void handleSetGoods(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_GOODS, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 9) {
            sendErrorResponse(CMD_SET_GOODS, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte goodsType = params[4];
        byte quantity = params[5];
        int price = (params[6] & 0xFF) | ((params[7] & 0xFF) << 8) | ((params[8] & 0xFF) << 16);
        
        System.out.println("Goods set: type=" + goodsType + ", qty=" + quantity + ", price=" + price);
        sendSuccessResponse(CMD_SET_GOODS);
    }
    
    private void handleGetScaleStatus() {
        byte[] response = new byte[11];
        response[0] = CMD_GET_SCALE_STATUS;
        response[1] = ERR_SUCCESS;
        
        int status = 0;
        if (weightStable) status |= 0x0010;
        if (weightOverweight) status |= 0x0040;
        if (currentWeight == 0) status |= 0x0001;
        response[2] = (byte)(status & 0xFF);
        response[3] = (byte)((status >> 8) & 0xFF);
        
        long weightToSend = currentWeight;
        for (int i = 0; i < 8; i++) {
            response[4 + i] = (byte)((weightToSend >> (i * 8)) & 0xFF);
        }
        
        sendResponse(response);
    }
    
    private void handleGetScaleStatusExt() {
        byte[] response = new byte[15];
        response[0] = CMD_GET_SCALE_STATUS_EXT;
        response[1] = ERR_SUCCESS;
        
        int status = 0;
        if (weightStable) status |= 0x0010;
        if (weightOverweight) status |= 0x0040;
        if (currentWeight == 0) status |= 0x0001;
        response[2] = (byte)(status & 0xFF);
        response[3] = (byte)((status >> 8) & 0xFF);
        
        long weightToSend = currentWeight;
        for (int i = 0; i < 8; i++) {
            response[4 + i] = (byte)((weightToSend >> (i * 8)) & 0xFF);
        }
        
        long tareToSend = currentTare;
        for (int i = 0; i < 4; i++) {
            response[12 + i] = (byte)((tareToSend >> (i * 8)) & 0xFF);
        }
        
        sendResponse(response);
    }
    
    private void handleWriteCalibPoint(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_WRITE_CALIB_POINT, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_WRITE_CALIB_POINT, ERR_INVALID_PASSWORD);
            return;
        }
        
        sendSuccessResponse(CMD_WRITE_CALIB_POINT);
    }
    
    private void handleReadCalibPoint(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_READ_CALIB_POINT, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        byte[] response = new byte[7];
        response[0] = CMD_READ_CALIB_POINT;
        response[1] = ERR_SUCCESS;
        response[2] = 0;
        response[3] = 0;
        response[4] = 0;
        response[5] = 0;
        response[6] = 0;
        
        sendResponse(response);
    }
    
    private void handleStartCalib(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_START_CALIB, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        sendSuccessResponse(CMD_START_CALIB);
    }
    
    private void handleGetCalibStatus(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_GET_CALIB_STATUS, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_GET_CALIB_STATUS, ERR_INVALID_PASSWORD);
            return;
        }
        
        byte[] response = new byte[6];
        response[0] = CMD_GET_CALIB_STATUS;
        response[1] = ERR_SUCCESS;
        response[2] = (byte)selectedChannel;
        response[3] = 0;
        response[4] = 0;
        response[5] = 2;
        
        sendResponse(response);
    }
    
    private void handleAbortCalib(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_ABORT_CALIB, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_ABORT_CALIB, ERR_INVALID_PASSWORD);
            return;
        }
        
        sendSuccessResponse(CMD_ABORT_CALIB);
    }
    
    private void handleGetAdc(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_GET_ADC, ERR_INVALID_PASSWORD);
            return;
        }
        
        byte[] response = new byte[6];
        response[0] = CMD_GET_ADC;
        response[1] = ERR_SUCCESS;
        
        int adcValue = (int)(currentWeight * 100);
        response[2] = (byte)(adcValue & 0xFF);
        response[3] = (byte)((adcValue >> 8) & 0xFF);
        response[4] = (byte)((adcValue >> 16) & 0xFF);
        response[5] = (byte)((adcValue >> 24) & 0xFF);
        
        sendResponse(response);
    }
    
    private void handleGetKeyboard(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_GET_KEYBOARD, ERR_INVALID_PASSWORD);
            return;
        }
        
        byte[] response = new byte[3];
        response[0] = CMD_GET_KEYBOARD;
        response[1] = ERR_SUCCESS;
        response[2] = (byte)(keyboardLocked ? 1 : 0);
        
        sendResponse(response);
    }
    
    private void handleGetChannelsCount() {
        byte[] response = new byte[3];
        response[0] = CMD_GET_CHANNELS_COUNT;
        response[1] = ERR_SUCCESS;
        response[2] = 1;
        
        sendResponse(response);
    }
    
    private void handleSelectChannel(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SELECT_CHANNEL, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 5) {
            sendErrorResponse(CMD_SELECT_CHANNEL, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        int channel = params[4] & 0xFF;
        if (channel < 0 || channel > 0) {
            sendErrorResponse(CMD_SELECT_CHANNEL, ERR_INVALID_CHANNEL);
            return;
        }
        
        selectedChannel = channel;
        sendSuccessResponse(CMD_SELECT_CHANNEL);
    }
    
    private void handleSetChannelState(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_CHANNEL_STATE, ERR_INVALID_PASSWORD);
            return;
        }
        
        if (params.length < 5) {
            sendErrorResponse(CMD_SET_CHANNEL_STATE, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        byte state = params[4];
        if (state != 0 && state != 1) {
            sendErrorResponse(CMD_SET_CHANNEL_STATE, ERR_INVALID_PARAM);
            return;
        }
        
        System.out.println("Channel " + selectedChannel + " state set to: " + (state == 1 ? "ON" : "OFF"));
        sendSuccessResponse(CMD_SET_CHANNEL_STATE);
    }
    
    private void handleGetChannelParams(byte[] params) {
        if (params.length < 1) {
            sendErrorResponse(CMD_GET_CHANNEL_PARAMS, ERR_INVALID_DATA_LENGTH);
            return;
        }
        
        int channel = params[0] & 0xFF;
        if (channel < 0 || channel > 0) {
            sendErrorResponse(CMD_GET_CHANNEL_PARAMS, ERR_INVALID_CHANNEL);
            return;
        }
        
        byte[] response = new byte[25];
        response[0] = CMD_GET_CHANNEL_PARAMS;
        response[1] = ERR_SUCCESS;
        
        response[2] = (byte)(channelParams.flags & 0xFF);
        response[3] = (byte)((channelParams.flags >> 8) & 0xFF);
        response[4] = channelParams.decimalPoint;
        response[5] = (byte)channelParams.power;
        response[6] = (byte)(channelParams.npv & 0xFF);
        response[7] = (byte)((channelParams.npv >> 8) & 0xFF);
        response[8] = (byte)(channelParams.nmpv & 0xFF);
        response[9] = (byte)((channelParams.nmpv >> 8) & 0xFF);
        response[10] = (byte)(channelParams.maxTare & 0xFF);
        response[11] = (byte)((channelParams.maxTare >> 8) & 0xFF);
        response[12] = (byte)(channelParams.range1 & 0xFF);
        response[13] = (byte)((channelParams.range1 >> 8) & 0xFF);
        response[14] = (byte)(channelParams.range2 & 0xFF);
        response[15] = (byte)((channelParams.range2 >> 8) & 0xFF);
        response[16] = (byte)(channelParams.range3 & 0xFF);
        response[17] = (byte)((channelParams.range3 >> 8) & 0xFF);
        response[18] = channelParams.discr1;
        response[19] = channelParams.discr2;
        response[20] = channelParams.discr3;
        response[21] = channelParams.discr4;
        response[22] = channelParams.calibPoints;
        response[23] = 0;
        response[24] = 0;
        
        sendResponse(response);
    }
    
    private void handleSetChannelParams(byte[] params) {
        if (currentMode != MODE_CALIBRATION) {
            sendErrorResponse(CMD_SET_CHANNEL_PARAMS, ERR_COMMAND_NOT_AVAILABLE);
            return;
        }
        
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_SET_CHANNEL_PARAMS, ERR_INVALID_PASSWORD);
            return;
        }
        
        sendSuccessResponse(CMD_SET_CHANNEL_PARAMS);
    }
    
    private void handleGetCurrentChannel() {
        byte[] response = new byte[3];
        response[0] = CMD_GET_CURRENT_CHANNEL;
        response[1] = ERR_SUCCESS;
        response[2] = (byte)selectedChannel;
        
        sendResponse(response);
    }
    
    private void handleResetChannel(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_RESET_CHANNEL, ERR_INVALID_PASSWORD);
            return;
        }
        
        System.out.println("Channel " + selectedChannel + " reset");
        sendSuccessResponse(CMD_RESET_CHANNEL);
    }
    
    private void handleReset(byte[] params) {
        if (!checkPassword(params, 0)) {
            sendErrorResponse(CMD_RESET, ERR_INVALID_PASSWORD);
            return;
        }
        
        System.out.println("Device reset");
        sendSuccessResponse(CMD_RESET);
    }
    
    private void handleGetDeviceType() {
        byte[] nameBytes = "Scale Emulator".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] response = new byte[8 + nameBytes.length];
        
        response[0] = CMD_GET_DEVICE_TYPE;
        response[1] = ERR_SUCCESS;
        response[2] = 0x01;
        response[3] = 0x01;
        response[4] = 0x01;
        response[5] = 0x03;
        response[6] = 0x64;
        response[7] = 0x00;
        
        System.arraycopy(nameBytes, 0, response, 8, nameBytes.length);
        
        sendResponse(response);
    }
    
    // ========== Вспомогательные методы ==========
    
    private boolean checkPassword(byte[] params, int offset) {
        if (params.length < offset + 4) return false;
        
        for (int i = 0; i < 4; i++) {
            if (params[offset + i] != adminPassword[i]) {
                return false;
            }
        }
        return true;
    }
    
    private void sendSuccessResponse(byte command) {
        byte[] response = new byte[2];
        response[0] = command;
        response[1] = ERR_SUCCESS;
        sendResponse(response);
    }
    
    private void sendErrorResponse(byte command, byte errorCode) {
        byte[] response = new byte[2];
        response[0] = command;
        response[1] = errorCode;
        sendResponse(response);
        errorCount++;
    }
    
    private void sendResponse(byte[] data) {
        try {
            int length = data.length;
            
            outputStream.write(STX);
            outputStream.write((byte)length);
            outputStream.write(data);
            
            byte lrc = calculateLRC(STX, (byte)length, data);
            outputStream.write(lrc);
            outputStream.flush();
            
            System.out.println("Sent response, length: " + length + ", command: 0x" + 
                Integer.toHexString(data[0] & 0xFF));
        } catch (IOException e) {
            errorCount++;
        }
    }
    
    // ========== Внутренние классы ==========
    
    private static class ChannelParams {
        int flags = 0x0001;
        byte decimalPoint = 3;
        int power = -3;
        int npv = 30000;
        int nmpv = 20;
        int maxTare = 15000;
        int range1 = 15000;
        int range2 = 30000;
        int range3 = 0;
        byte discr1 = 1;
        byte discr2 = 2;
        byte discr3 = 5;
        byte discr4 = 10;
        byte calibPoints = 3;
    }
    
    private static class Command {
        enum Type { TARE, ZERO }
        final Type type;
        final long value;
        
        Command(Type type, long value) {
            this.type = type;
            this.value = value;
        }
    }
}