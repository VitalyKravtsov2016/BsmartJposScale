package com.bsmart.scale;

import java.util.prefs.*;
import java.awt.Component;
import javax.swing.JOptionPane;
import java.util.Enumeration;
import java.util.Vector;
import com.bsmart.IDevice;
import com.bsmart.DeviceError;
import com.bsmart.port.GnuSerialPort;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SmScale {

    private final Logger logger = LogManager.getLogger(SmScale.class);

    private int portType = IDevice.PARAM_PORTTYPE_SERIAL;
    private String portName = "COM1";
    private int pointNumber = 1;
    private int baudRate = 9600;
    private int timeout = 100;
    private String password = "30";

    private DeviceMetrics deviceMetrics;
    private int channelCount = 0;
    private int channelNumber = 0;
    private ChannelParams scaleChannel = new ChannelParams();
    private boolean connected = false;
    private boolean calibration = false;
    private double calibrationProgress = 0;
    private final Pos2Serial protocol = new Pos2Serial();
    public static final SmScale instance = new SmScale();
    private boolean testMode = false;

    private SmScale() {
        loadParams();
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public String getDefaultPortName() {
        String result = "COM1";
        Vector<String> portNames = GnuSerialPort.getPortList();
        if (portNames.size() > 0) {
            result = portNames.get(0);
        }
        return result;
    }

    public void setDefaults() {
        logger.error("setDefaults");

        portName = getDefaultPortName();
        baudRate = 9600;
        timeout = 100;
        password = "30";

        protocol.setParam(IDevice.PARAM_DATABITS, "8");
        protocol.setParam(IDevice.PARAM_STOPBITS, "1");
        protocol.setParam(IDevice.PARAM_PARITY, "0");
        protocol.setParam(IDevice.PARAM_PASSWORD, password);
        protocol.setParam(IDevice.PARAM_APPNAME, "Программа градуировки");
        protocol.setParam(IDevice.PARAM_OPEN_TIMEOUT, String.valueOf(timeout));
    }

    public void loadParams() {
        logger.debug("loadParams()");
        try {
            Preferences prefs = Preferences.userNodeForPackage(SmScale.class);
            portName = prefs.get("portName", getDefaultPortName());
            baudRate = prefs.getInt("baudRate", 9600);
            timeout = prefs.getInt("timeout", 100);
            password = prefs.get("password", "30");

            protocol.setParam(IDevice.PARAM_DATABITS, "8");
            protocol.setParam(IDevice.PARAM_STOPBITS, "1");
            protocol.setParam(IDevice.PARAM_PARITY, "0");
            protocol.setParam(IDevice.PARAM_PASSWORD, password);
            protocol.setParam(IDevice.PARAM_APPNAME, "Программа градуировки");
            protocol.setParam(IDevice.PARAM_OPEN_TIMEOUT, String.valueOf(timeout));

            logger.debug("loadParams(): OK");
        } catch (Exception e) {
            logger.error(e.getMessage());
            setDefaults();
        }
    }

    public void saveParams() {
        Preferences prefs = Preferences.userNodeForPackage(SmScale.class);
        prefs.put("portName", portName);
        prefs.putInt("baudRate", baudRate);
        prefs.putInt("timeout", timeout);
        prefs.put("password", password);

        protocol.setParam(IDevice.PARAM_PORTTYPE, String.valueOf(portType));
        protocol.setParam(IDevice.PARAM_PORTNAME, portName);
        protocol.setParam(IDevice.PARAM_BAUDRATE, String.valueOf(baudRate));
        protocol.setParam(IDevice.PARAM_DATABITS, "8");
        protocol.setParam(IDevice.PARAM_STOPBITS, "1");
        protocol.setParam(IDevice.PARAM_PARITY, "0");
        protocol.setParam(IDevice.PARAM_PASSWORD, password);
        protocol.setParam(IDevice.PARAM_APPNAME, "Программа градуировки");
        protocol.setParam(IDevice.PARAM_OPEN_TIMEOUT, String.valueOf(timeout));
    }

    public String getPortName() {
        return portName;
    }

    public void setPortType(int portType) {
        this.portType = portType;
    }

    public int getPortType() {
        return portType;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceName() {
        String result = "";
        if (deviceMetrics != null) {
            result = deviceMetrics.getDescription();
        }
        return result;
    }

    public String getDeviceText() {
        String result = "";
        if (deviceMetrics != null) {
            result = deviceMetrics.toString();
        }
        return result;
    }

    public int getChannelCount() 
    {
        return channelCount;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isConnected() {
        return connected;
    }

    public DeviceMetrics getDeviceMetrics() {
        return deviceMetrics;
    }

    public void connect() throws Exception 
    {
        if (testMode) {
            channelCount = 1;
            channelNumber = 1;
            connected = true;
            return;
        }
            
        if (connected) {
            return;
        }
        protocol.connect();
        protocol.readDeviceMetrics();
        deviceMetrics = protocol.getDeviceMetrics();
        protocol.readChannelCount();
        channelCount = protocol.getChannelCount();
        protocol.readChannelNumber();
        channelNumber = protocol.getChannelNumber();
        connected = true;
        cancelCalibration();
    }

    public void disconnect() {
        if (testMode) return;
        
        try {
            if (connected) {
                cancelCalibration();
                protocol.disconnect();
            }
        } catch (Exception e) {
            // !!!
        }
    }

    public void cancelCalibration() throws Exception {
        if (testMode) return;
        
        if (calibration) {
            protocol.readScaleMode();
            if (protocol.getScaleMode() == protocol.MODE_CALIBRATION) {
                stopCalibration();
                exitCalibrationMode();
            }
        }
    }

    public int readPortParams() {
        return 0;
    }

    public ChannelParams getChannelParams() 
    {
        if (testMode) 
        {
            ChannelParams channelParams = new ChannelParams();
            channelParams.flags = 0;
            channelParams.decimalPoint = 2;
            channelParams.power = 0;
            channelParams.maxWeigth = 15000;
            channelParams.minWeigth = 40;
            channelParams.maxTare = 12000;
            channelParams.range[0] = 0;
            channelParams.range[1] = 0;
            channelParams.range[2] = 0;
            channelParams.range[3] = 0;
            channelParams.resolution[0] = 0;
            channelParams.resolution[1] = 0;
            channelParams.resolution[2] = 0;
            channelParams.resolution[3] = 0;
            channelParams.pointCount = 5;
            channelParams.calibCount = 5;
            return channelParams;
        }
        return protocol.getChannelParams();
    }

    public void readChannelParams(int index) throws Exception 
    {
        if (testMode) return;
        
        protocol.readChannelParams(index);
    }

    public long getADCValue() {
        long adcValue = protocol.getADCValue();
        return adcValue;
    }

    public void readADCValue() throws Exception 
    {
        if (testMode) return;
        protocol.readADCValue();
    }

    public int getPointNumber() {
        return pointNumber;
    }

    public void incPointNumber() {
        pointNumber++;
    }

    public void startCalibration() throws Exception 
    {
        if (testMode) return;
        
        calibrationProgress = 0;
        protocol.startCalibration();
        calibration = true;
    }

    public void stopCalibration() throws Exception 
    {
        if (testMode) return;
        
        protocol.stopCalibration();
    }

    public void resetPointNumber() {
        pointNumber = 1;
    }

    public void enterCalibrationMode() throws Exception 
    {
        if (testMode) return;
        
        int rc = 0;
        try {
            protocol.lockKeyboard();
        } catch (DeviceError e) {
            rc = e.getCode();
        }
        if ((rc == 0) || (rc == IScale.ERROR_CMD_NOT_IMPL_IN_INTERFACE)) {
            protocol.writeMode(protocol.MODE_CALIBRATION);
        }
    }

    public void exitCalibrationMode() throws Exception 
    {
        if (testMode) return;
        
        protocol.writeMode(protocol.MODE_NORMAL);
        protocol.unlockKeyboard();
    }

    public void sendCalibrationPassword() throws Exception 
    {
        if (testMode) return;
        
        boolean result = true;
        int[] password = {60, 60, 188, 61, 189, 61, 61, 189, 60, 188};
        for (int i = 0; i < password.length; i++) {
            protocol.sendKey(password[i]);
        }
    }

    public void readScaleMode() throws Exception {
        if (testMode) return;
        protocol.readScaleMode();
    }

    public int getScaleMode() {
        return protocol.getScaleMode();
    }

    public CalibrationStatus getCalibrationStatus() {
        return protocol.getCalibrationStatus();
    }

    public void readCalibrationStatus() throws Exception {
        if (testMode) return;
        protocol.readCalibrationStatus();
    }

    public double getWeightValue(int value) {
        return protocol.getWeightValue(value);
    }

    public void stepCalibrationProgress() {
        calibrationProgress += 2.4;
    }

    public int getCalibrationProgress() {
        return (int) calibrationProgress;
    }
}
