package com.bsmart.jpos.scale;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.bsmart.jpos.JposPropertyReader;

import com.bsmart.IDevice;
import com.bsmart.DeviceError;
import com.bsmart.scale.DeviceMetrics;
import com.bsmart.port.GnuSerialPort;
import com.bsmart.scale.EScale;
import com.bsmart.scale.IScale;
import com.bsmart.scale.Pos2Serial;
import com.bsmart.scale.ScaleSerial;
import com.bsmart.scale.ScaleWeight;
import com.bsmart.scale.Shtrih5Serial;
import com.bsmart.scale.Shtrih6Serial;
import com.bsmart.tools.Tools;
import com.bsmart.tools.StringParams;
import com.bsmart.jpos.JposUtils;
import com.bsmart.util.ServiceVersionUtil;

import jpos.JposConst;
import jpos.ScaleConst;
import jpos.JposException;
import jpos.config.JposEntryConst;
import jpos.config.RS232Const;
import jpos.events.DataEvent;
import jpos.events.JposEvent;
import jpos.events.ErrorEvent;
import jpos.events.DirectIOEvent;
import jpos.events.StatusUpdateEvent;
import jpos.events.OutputCompleteEvent;
import jpos.services.EventCallbacks;
import jpos.services.ScaleService113;

/**
 * Реализация сервиса весов для JPOS 1.13 с полным логированием
 */
public class ScaleService extends Scale implements ScaleService113, ScaleConst, JposConst, JposEntryConst {

    private static final long serialVersionUID = 6309237509625068100L;
    private final Logger logger = LogManager.getLogger(ScaleService.class);

    // Константы
    private static final int POLL_INTERVAL_MS = 100;
    private static final int THREAD_STOP_TIMEOUT_MS = 1000;
    private static final int MAX_RETRY_COUNT = 3;

    // Свойства UPOS
    private boolean zeroValid = false;
    private int statusNotify = SCAL_SN_DISABLED;
    private int powerNotify = JPOS_PN_DISABLED;
    private int powerState = JPOS_PS_UNKNOWN;
    private boolean deviceEnabled = false;
    private boolean claimed = false;
    private boolean asyncMode = false;
    private boolean dataEventEnabled = false;
    private boolean freezeEvents = false;
    private boolean autoDisable = false;
    private int tareWeight = 0;
    private long unitPrice = 0;
    private long salesPrice = 0;

    // Состояние устройства
    private int state = JPOS_S_CLOSED;
    private ScaleSerial scale = null;
    private DeviceMetrics deviceMetrics;
    private ScaleWeight currentWeight = null;
    private long scaleLiveWeight = 0;
    private int maximumWeight = 0;
    private int minimumWeight = 0;
    private int weightUnit = SCAL_WU_GRAM;

    // Потоки и очереди
    private EventCallbacks eventsCallback = null;
    private String logicalName = null;
    private Thread eventThread = null;
    private Thread pollThread = null;
    private Thread weightThread = null;
    private final BlockingQueue<JposEvent> eventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<WeightRequest> requestQueue = new LinkedBlockingQueue<>();

    // Управление запросами
    private volatile WeightRequest currentRequest = null;
    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);

    // Флаги
    private boolean pollEnabled = true;

    // ======================== ВНУТРЕННИЕ КЛАССЫ ========================
    private static class WeightRequest {

        private final int id;
        private final int timeout;
        private int retryCount = 0;

        WeightRequest(int timeout) {
            this.id = this.hashCode();
            this.timeout = timeout;
        }

        public int getId() {
            return id;
        }

        public int getTimeout() {
            return timeout;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void incrementRetry() {
            retryCount++;
        }

        public WeightRequest copy() {
            WeightRequest copy = new WeightRequest(this.timeout);
            copy.retryCount = this.retryCount + 1;
            return copy;
        }
    }

    private class EventTarget implements Runnable {

        private final ScaleService service;

        public EventTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.eventProc();
        }
    }

    private class PollTarget implements Runnable {

        private final ScaleService service;

        public PollTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.pollProc();
        }
    }

    private class WeightTarget implements Runnable {

        private final ScaleService service;

        public WeightTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.weightProc();
        }
    }

    // ======================== УПРАВЛЕНИЕ СОСТОЯНИЕМ ========================
    private void setState(int newState) {
        if (this.state != newState) {
            logger.debug("State changing from " + JposUtils.getStateText(this.state)
                    + " to " + JposUtils.getStateText(newState));
            this.state = newState;
        }
    }

    private void checkOpened() throws JposException {
        if (state == JPOS_S_CLOSED) {
            logger.debug("checkOpened() JPOS_E_CLOSED");
            throw new JposException(JPOS_E_CLOSED, "Device is closed");
        }
    }

    private void checkClaimed() throws JposException {
        if (!claimed) {
            logger.debug("checkClaimed() JPOS_E_NOTCLAIMED");
            throw new JposException(JPOS_E_NOTCLAIMED, "Device not claimed");
        }
    }

    private void checkEnabled() throws JposException {
        if (!deviceEnabled) {
            logger.debug("checkEnabled() JPOS_E_DISABLED");
            throw new JposException(JPOS_E_DISABLED, "Device not enabled");
        }
    }

    private void checkDisabled() throws JposException {
        if (deviceEnabled) {
            logger.debug("checkDisabled() JPOS_E_ILLEGAL");
            throw new JposException(JPOS_E_ILLEGAL, "Device must be disabled for this operation");
        }
    }

    /**
     * Проверяет, что текущий поток не является потоком доставки событий.
     * Вызов методов, изменяющих жизненный цикл устройства, из потока событий
     * может привести к взаимной блокировке (deadlock).
     *
     * @throws JposException с кодом JPOS_E_ILLEGAL, если вызов из потока событий
     */
    private void checkNotInEventThread() throws JposException {
        if (Thread.currentThread() == eventThread) {
            logger.error("Method called from event handler thread - this would cause deadlock");
            throw new JposException(JPOS_E_ILLEGAL, 
                "Method cannot be called from event handler thread");
        }
    }

    // ======================== CAPABILITY СВОЙСТВА ========================
    @Override
    public boolean getCapCompareFirmwareVersion() throws JposException {
        logger.debug("getCapCompareFirmwareVersion");
        checkOpened();
        boolean result = false;
        logger.debug("getCapCompareFirmwareVersion: " + result);
        return result;
    }

    @Override
    public boolean getCapStatusUpdate() throws JposException {
        logger.debug("getCapStatusUpdate");
        checkOpened();
        boolean result = true;
        logger.debug("getCapStatusUpdate: " + result);
        return result;
    }

    @Override
    public boolean getCapUpdateFirmware() throws JposException {
        logger.debug("getCapUpdateFirmware");
        checkOpened();
        boolean result = false;
        logger.debug("getCapUpdateFirmware: " + result);
        return result;
    }

    @Override
    public boolean getCapDisplay() throws JposException {
        logger.debug("getCapDisplay");
        checkOpened();
        boolean result = false;
        logger.debug("getCapDisplay: " + result);
        return result;
    }

    @Override
    public boolean getCapStatisticsReporting() throws JposException {
        logger.debug("getCapStatisticsReporting");
        checkOpened();
        boolean result = false;
        logger.debug("getCapStatisticsReporting: " + result);
        return result;
    }

    @Override
    public boolean getCapUpdateStatistics() throws JposException {
        logger.debug("getCapUpdateStatistics");
        checkOpened();
        boolean result = false;
        logger.debug("getCapUpdateStatistics: " + result);
        return result;
    }

    @Override
    public boolean getCapDisplayText() throws JposException {
        logger.debug("getCapDisplayText");
        checkOpened();
        boolean result = false;
        logger.debug("getCapDisplayText: " + result);
        return result;
    }

    @Override
    public int getCapPowerReporting() throws JposException {
        logger.debug("getCapPowerReporting");
        checkOpened();
        int result = JPOS_PR_STANDARD;
        logger.debug("getCapPowerReporting: " + result);
        return result;
    }

    @Override
    public boolean getCapPriceCalculating() throws JposException {
        logger.debug("getCapPriceCalculating");
        checkOpened();
        boolean result = false;
        logger.debug("getCapPriceCalculating: " + result);
        return result;
    }

    @Override
    public boolean getCapTareWeight() throws JposException {
        logger.debug("getCapTareWeight");
        checkOpened();
        boolean result = true;
        logger.debug("getCapTareWeight: " + result);
        return result;
    }

    @Override
    public boolean getCapZeroScale() throws JposException {
        logger.debug("getCapZeroScale");
        checkOpened();
        boolean result = scale != null && scale.getType() == EScale.Pos2;
        logger.debug("getCapZeroScale: " + result);
        return result;
    }

    // ======================== СВОЙСТВА ========================
    public int getMinimumWeight() throws JposException {
        logger.debug("getMinimumWeight");
        checkOpened();
        logger.debug("getMinimumWeight: " + minimumWeight);
        return minimumWeight;
    }

    @Override
    public int getMaximumWeight() throws JposException {
        logger.debug("getMaximumWeight");
        checkOpened();
        logger.debug("getMaximumWeight: " + maximumWeight);
        return maximumWeight;
    }

    @Override
    public int getWeightUnit() throws JposException {
        logger.debug("getWeightUnit");
        checkOpened();
        logger.debug("getWeightUnit: " + weightUnit);
        return weightUnit;
    }

    @Override
    public int getScaleLiveWeight() throws JposException {
        logger.debug("getScaleLiveWeight");
        checkEnabled();
        logger.debug("getScaleLiveWeight: " + scaleLiveWeight);
        return (int) scaleLiveWeight;
    }

    @Override
    public int getStatusNotify() throws JposException {
        logger.debug("getStatusNotify");
        checkOpened();
        logger.debug("getStatusNotify: " + statusNotify);
        return statusNotify;
    }

    @Override
    public void setStatusNotify(int statusNotify) throws JposException {
        logger.debug("setStatusNotify(" + statusNotify + ")");
        checkDisabled();
        this.statusNotify = statusNotify;
        logger.debug("setStatusNotify: OK");
    }

    @Override
    public boolean getAsyncMode() throws JposException {
        logger.debug("getAsyncMode");
        checkOpened();
        logger.debug("getAsyncMode: " + asyncMode);
        return asyncMode;
    }

    @Override
    public void setAsyncMode(boolean async) throws JposException {
        logger.debug("setAsyncMode(" + async + ")");
        checkOpened();
        checkNotInEventThread(); // Защита от deadlock

        if (async == this.asyncMode) {
            logger.debug("setAsyncMode: no change");
            return;
        }

        this.asyncMode = async;
        if (this.asyncMode) {
            startWeightThread();
        } else {
            stopWeightThread();
            if (state == JPOS_S_BUSY) {
                setState(JPOS_S_IDLE);
            }
        }
        logger.debug("setAsyncMode: OK");
    }

    @Override
    public int getDataCount() throws JposException {
        logger.debug("getDataCount");
        checkOpened();
        int count = eventQueue.size();
        logger.debug("getDataCount: " + count);
        return count;
    }

    @Override
    public int getMaxDisplayTextChars() throws JposException {
        logger.debug("getMaxDisplayTextChars");
        checkOpened();
        logger.debug("getMaxDisplayTextChars: 0");
        return 0;
    }

    @Override
    public int getPowerNotify() throws JposException {
        logger.debug("getPowerNotify");
        checkOpened();
        logger.debug("getPowerNotify: " + powerNotify);
        return powerNotify;
    }

    @Override
    public void setPowerNotify(int powerNotify) throws JposException {
        logger.debug("setPowerNotify(" + powerNotify + ")");
        checkDisabled();
        this.powerNotify = powerNotify;
        logger.debug("setPowerNotify: OK");
    }

    @Override
    public int getPowerState() throws JposException {
        logger.debug("getPowerState");
        checkOpened();
        logger.debug("getPowerState: " + powerState);
        return powerState;
    }

    @Override
    public long getSalesPrice() throws JposException {
        logger.debug("getSalesPrice");
        checkEnabled();
        logger.debug("getSalesPrice: " + salesPrice);
        return salesPrice;
    }

    @Override
    public int getTareWeight() throws JposException {
        logger.debug("getTareWeight");
        checkEnabled();
        logger.debug("getTareWeight: " + tareWeight);
        return tareWeight;
    }

    @Override
    public void setTareWeight(int tareWeight) throws JposException {
        logger.debug("setTareWeight(" + tareWeight + ")");
        checkEnabled();
        checkNotInEventThread(); // Защита от deadlock

        if (!getCapTareWeight()) {
            JposException e = new JposException(JPOS_E_ILLEGAL, "Tare weight not supported");
            logger.error("setTareWeight: " + e.getMessage());
            throw e;
        }

        try {
            this.tareWeight = tareWeight;
            scale.tara((long) tareWeight);
            logger.debug("setTareWeight: OK");
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("setTareWeight error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public long getUnitPrice() throws JposException {
        logger.debug("getUnitPrice");
        checkEnabled();
        logger.debug("getUnitPrice: " + unitPrice);
        return unitPrice;
    }

    @Override
    public void setUnitPrice(long unitPrice) throws JposException {
        logger.debug("setUnitPrice(" + unitPrice + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("setUnitPrice: " + e.getMessage());
        throw e;
    }

    @Override
    public boolean getAutoDisable() throws JposException {
        logger.debug("getAutoDisable");
        checkOpened();
        logger.debug("getAutoDisable: " + autoDisable);
        return autoDisable;
    }

    @Override
    public void setAutoDisable(boolean autoDisable) throws JposException {
        logger.debug("setAutoDisable(" + autoDisable + ")");
        checkOpened();
        this.autoDisable = autoDisable;
        logger.debug("setAutoDisable: OK");
    }

    @Override
    public boolean getZeroValid() throws JposException {
        logger.debug("getZeroValid");
        checkOpened();
        logger.debug("getZeroValid: " + zeroValid);
        return zeroValid;
    }

    @Override
    public void setZeroValid(boolean zeroValid) throws JposException {
        logger.debug("setZeroValid(" + zeroValid + ")");
        checkOpened();
        this.zeroValid = zeroValid;
        logger.debug("setZeroValid: OK");
    }

    @Override
    public boolean getDataEventEnabled() throws JposException {
        logger.debug("getDataEventEnabled");
        checkOpened();
        logger.debug("getDataEventEnabled: " + dataEventEnabled);
        return dataEventEnabled;
    }

    @Override
    public void setDataEventEnabled(boolean enabled) throws JposException {
        logger.debug("setDataEventEnabled(" + enabled + ")");
        checkOpened();
        synchronized (this) {
            dataEventEnabled = enabled;
        }
        logger.debug("setDataEventEnabled: OK");
    }

    @Override
    public boolean getFreezeEvents() throws JposException {
        logger.debug("getFreezeEvents");
        checkOpened();
        logger.debug("getFreezeEvents: " + freezeEvents);
        return freezeEvents;
    }

    @Override
    public void setFreezeEvents(boolean freezeEvents) throws JposException {
        logger.debug("setFreezeEvents(" + freezeEvents + ")");
        checkOpened();

        synchronized (this) {
            this.freezeEvents = freezeEvents;
        }

        logger.debug("setFreezeEvents: OK");
    }

    @Override
    public boolean getClaimed() throws JposException {
        logger.debug("getClaimed");
        checkOpened();
        logger.debug("getClaimed: " + claimed);
        return claimed;
    }

    @Override
    public boolean getDeviceEnabled() throws JposException {
        logger.debug("getDeviceEnabled");
        checkOpened();
        logger.debug("getDeviceEnabled: " + deviceEnabled);
        return deviceEnabled;
    }

    @Override
    public int getState() throws JposException {
        logger.debug("getState");
        logger.debug("getState: " + JposUtils.getStateText(state));
        return state;
    }

    // ======================== МЕТОДЫ УПРАВЛЕНИЯ ========================
    @Override
    public void open(String logicalName, EventCallbacks eventsCallback) throws JposException {
        logger.debug("open(" + logicalName + ", " + eventsCallback + ")");

        if (state != JPOS_S_CLOSED) {
            logger.warn("open: state != JPOS_S_CLOSED");
            return;
        }

        this.eventsCallback = eventsCallback;
        this.logicalName = logicalName;
        this.asyncMode = false;
        this.deviceEnabled = false;
        this.claimed = false;

        StringParams params = new StringParams();
        params.set(IDevice.PARAM_PORTNAME, "");
        params.set(IDevice.PARAM_DATABITS, "8");
        params.set(IDevice.PARAM_STOPBITS, "1");
        params.set(IDevice.PARAM_PARITY, "0");
        params.set(IDevice.PARAM_PASSWORD, "30");
        params.set(IDevice.PARAM_OPEN_TIMEOUT, "1000");
        params.set(IDevice.PARAM_READ_TIMEOUT, "1000");
        params.set(IDevice.PARAM_PORTTYPE, "0");

        String protocol = "pos2";

        try {
            if (m_jposEntry != null) {
                JposPropertyReader reader = new JposPropertyReader(m_jposEntry);
                protocol = reader.readString("protocol").toLowerCase();

                String value = reader.readString(RS232Const.RS232_PORT_NAME_PROP_NAME, "");
                params.set(IDevice.PARAM_PORTNAME, value);

                value = reader.readString(RS232Const.RS232_BAUD_RATE_PROP_NAME, "9600");
                params.set(IDevice.PARAM_BAUDRATE, value);

                value = reader.readString("password", "30");
                params.set(IDevice.PARAM_PASSWORD, value);

                value = reader.readString("timeout", "1000");
                params.set(IDevice.PARAM_OPEN_TIMEOUT, value);

                value = reader.readString("readTimeout", "1000");
                params.set(IDevice.PARAM_READ_TIMEOUT, value);

                value = reader.readString("portType", "0");
                params.set(IDevice.PARAM_PORTTYPE, value);
            }

            scale = createProtocol(protocol);
            scale.setParams(params);
            setState(JPOS_S_IDLE);
            logger.debug("open: OK");

        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("open error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public void close() throws JposException {
        logger.debug("close()");
        checkIdleState();
        checkNotInEventThread(); // Защита от deadlock

        try {
            if (getDeviceEnabled()) {
                setDeviceEnabled(false);
            }
            if (claimed) {
                release();
            }

            stopAllThreads();

            asyncMode = false;
            setState(JPOS_S_CLOSED);
            scale = null;
            currentWeight = null;

            requestQueue.clear();
            eventQueue.clear();

            logger.debug("close: OK");
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("close error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public void claim(int timeout) throws JposException {
        logger.debug("claim(" + timeout + ")");
        checkOpened();
        checkNotInEventThread(); // Защита от deadlock

        if (claimed) {
            logger.debug("claim: already claimed");
            return;
        }

        try {
            scale.openPort(timeout);
            deviceMetrics = scale.getDeviceMetrics();

            //maximumWeight = (int) deviceMetrics.getMaximumWeight();
            //minimumWeight = (int) deviceMetrics.getMinimumWeight();
            //weightUnit = mapWeightUnit(deviceMetrics.getWeightUnit());
            claimed = true;
            logger.debug(deviceMetrics.toString());
            logger.debug("claim: OK");

        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("claim error: " + e.getMessage());
            throw je;
        }
    }

    private int mapWeightUnit(String unit) {
        if ("kg".equalsIgnoreCase(unit)) {
            return SCAL_WU_KILOGRAM;
        }
        if ("g".equalsIgnoreCase(unit)) {
            return SCAL_WU_GRAM;
        }
        if ("lb".equalsIgnoreCase(unit)) {
            return SCAL_WU_POUND;
        }
        if ("oz".equalsIgnoreCase(unit)) {
            return SCAL_WU_OUNCE;
        }
        return SCAL_WU_GRAM;
    }

    @Override
    public void release() throws JposException {
        logger.debug("release()");
        checkOpened();
        checkIdleState();
        checkNotInEventThread(); // Защита от deadlock

        if (!claimed) {
            logger.debug("release: not claimed");
            return;
        }

        try {
            scale.disconnect();
            claimed = false;
            setState(JPOS_S_IDLE);
            logger.debug("release: OK");
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("release error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public void setDeviceEnabled(boolean enabled) throws JposException {
        logger.debug("setDeviceEnabled(" + enabled + ")");
        checkClaimed();
        checkIdleState();
        checkNotInEventThread(); // Защита от deadlock

        try {
            if (enabled) {
                tareWeight = 0;
                unitPrice = 0;
                salesPrice = 0;

                deviceEnabled = true;
                setState(JPOS_S_IDLE);
                readScaleWeight();
                setPowerState(JPOS_PS_ONLINE);

                startPollThread();
                startEventThread();
                logger.debug("setDeviceEnabled: device enabled");

            } else {
                deviceEnabled = false;
                setState(JPOS_S_IDLE);
                setPowerState(JPOS_PS_UNKNOWN);

                if (asyncMode) {
                    setAsyncMode(false);
                }

                stopPollThread();
                requestQueue.clear();
                stopEventThread();
                logger.debug("setDeviceEnabled: device disabled");
            }
            logger.debug("setDeviceEnabled: OK");
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("setDeviceEnabled error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public void deleteInstance() throws JposException {
        logger.debug("deleteInstance()");
    }

    // ======================== МЕТОДЫ ВВОДА/ВЫВОДА ========================
    @Override
    public void clearInput() throws JposException {
        logger.debug("clearInput()");
        checkOpened();

        eventQueue.clear();
        requestQueue.clear();
        currentRequest = null;
        setState(JPOS_S_IDLE);

        logger.debug("clearInput: OK");
    }

    public void clearInputProperties() throws JposException {
        logger.debug("clearInputProperties()");
        checkOpened();

        scaleLiveWeight = 0;
        currentWeight = null;
        salesPrice = 0;

        logger.debug("clearInputProperties: OK");
    }

    public void clearOutput() throws JposException {
        logger.debug("clearOutput()");
        logger.debug("clearOutput: OK");
    }

    public synchronized void checkIdleState() throws JposException {
        if (state == JPOS_S_ERROR) {
            throw new JposException(JPOS_E_FAILURE, "Device in error state");
        }
        if (state == JPOS_S_BUSY) {
            throw new JposException(JPOS_E_BUSY, "Asynchronous operation already in progress");
        }
        if (state == JPOS_S_CLOSED) {
            throw new JposException(JPOS_E_CLOSED, "Device is closed");
        }
    }

    @Override
    public void readWeight(int[] weightData, int timeout) throws JposException {
        logger.debug("readWeight(" + weightData + ", " + timeout + ")");
        checkEnabled();
        checkIdleState();
        checkNotInEventThread();

        try {
            if (asyncMode) {
                // Атомарная проверка и установка состояния
                synchronized (this) 
                {
                    checkIdleState();
                    setState(JPOS_S_BUSY);
                }
                requestQueue.offer(new WeightRequest(timeout));
                logger.debug("readWeight: async request queued");

            } else {
                long weight = readWeightSync(timeout);
                if (weightData != null && weightData.length > 0) {
                    weightData[0] = (int) weight;
                }
                logger.debug("readWeight: OK, weight=" + weight);
            }
        } catch (JposException e) {
            logger.error("readWeight error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("readWeight error: " + e.getMessage());
            throw je;
        }
    }

    @Override
    public void zeroScale() throws JposException {
        logger.debug("zeroScale()");
        checkEnabled();
        checkIdleState();
        checkNotInEventThread();

        if (!getCapZeroScale()) {
            JposException e = new JposException(JPOS_E_ILLEGAL, "Zero scale not supported");
            logger.error("zeroScale: " + e.getMessage());
            throw e;
        }

        try {
            scale.zero();
            logger.debug("zeroScale: OK");
        } catch (Exception e) {
            JposException je = getJposException(e);
            logger.error("zeroScale error: " + e.getMessage());
            throw je;
        }
    }

    // ======================== НЕПОДДЕРЖИВАЕМЫЕ МЕТОДЫ ========================
    @Override
    public void displayText(String text) throws JposException {
        logger.debug("displayText(" + text + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("displayText: " + e.getMessage());
        throw e;
    }

    @Override
    public void checkHealth(int level) throws JposException {
        logger.debug("checkHealth(" + level + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("checkHealth: " + e.getMessage());
        throw e;
    }

    @Override
    public void directIO(int command, int[] data, Object obj) throws JposException {
        logger.debug("directIO(" + command + ", " + data + ", " + obj + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("directIO: " + e.getMessage());
        throw e;
    }

    @Override
    public String getCheckHealthText() throws JposException {
        logger.debug("getCheckHealthText()");
        logger.debug("getCheckHealthText: ");
        return "";
    }

    @Override
    public void compareFirmwareVersion(String firmwareFileName, int[] result) throws JposException {
        logger.debug("compareFirmwareVersion(" + firmwareFileName + ", " + result + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("compareFirmwareVersion: " + e.getMessage());
        throw e;
    }

    @Override
    public void updateFirmware(String firmwareFileName) throws JposException {
        logger.debug("updateFirmware(" + firmwareFileName + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("updateFirmware: " + e.getMessage());
        throw e;
    }

    @Override
    public void resetStatistics(String statisticsBuffer) throws JposException {
        logger.debug("resetStatistics(" + statisticsBuffer + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("resetStatistics: " + e.getMessage());
        throw e;
    }

    @Override
    public void retrieveStatistics(String[] statisticsBuffer) throws JposException {
        logger.debug("retrieveStatistics(" + statisticsBuffer + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("retrieveStatistics: " + e.getMessage());
        throw e;
    }

    @Override
    public void updateStatistics(String statisticsBuffer) throws JposException {
        logger.debug("updateStatistics(" + statisticsBuffer + ")");
        JposException e = new JposException(JPOS_E_ILLEGAL, "Not supported");
        logger.error("updateStatistics: " + e.getMessage());
        throw e;
    }

    // ======================== ИНФОРМАЦИОННЫЕ МЕТОДЫ ========================
    @Override
    public String getDeviceServiceDescription() throws JposException {
        logger.debug("getDeviceServiceDescription()");
        String result = "Scale Service v1.13";
        logger.debug("getDeviceServiceDescription: " + result);
        return result;
    }

    @Override
    public int getDeviceServiceVersion() throws JposException {
        int version = 1013000 + ServiceVersionUtil.getVersionInt();
        logger.debug("getDeviceServiceVersion()");
        logger.debug("getDeviceServiceVersion: " + version);
        return version;
    }

    @Override
    public String getPhysicalDeviceDescription() throws JposException {
        logger.debug("getPhysicalDeviceDescription()");
        checkOpened();

        String result;
        if (scale == null) {
            result = "Unknown Scale";
        } else {
            switch (scale.getType()) {
                case Pos2:
                    result = "SHTRIKH-M POS2 Scale";
                    break;
                case Shtrih5:
                    result = "SHTRIKH-M SHTRIH5 Scale";
                    break;
                case Shtrih6:
                    result = "SHTRIKH-M SHTRIH6 Scale";
                    break;
                default:
                    result = "SHTRIKH-M Scale";
            }
        }
        logger.debug("getPhysicalDeviceDescription: " + result);
        return result;
    }

    @Override
    public String getPhysicalDeviceName() throws JposException {
        logger.debug("getPhysicalDeviceName()");
        String result = getPhysicalDeviceDescription();
        logger.debug("getPhysicalDeviceName: " + result);
        return result;
    }

    // ======================== СИНХРОННОЕ ЧТЕНИЕ ВЕСА ========================
    private long readWeightSync(int timeout) throws JposException, InterruptedException {
        long startTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            ScaleWeight weight = readScaleWeight();

            if (weight == null) {
                continue;
            }

            checkWeightErrors(weight);

            if (weight.status.isStable()) {
                if (weight.weight == 0) {
                    if (zeroValid) {
                        return weight.weight;
                    }
                } else {
                    return weight.weight;
                }
            }

            if (timeout == 0) {
                return weight.weight;
            }

            if (System.currentTimeMillis() > startTime + timeout) {
                throw new JposException(JPOS_E_TIMEOUT, "Timeout waiting for stable weight");
            }

            Thread.sleep(10);
        }

        throw new InterruptedException("Thread interrupted while reading weight");
    }

    private long readWeightAsync(int timeout) throws JposException, InterruptedException {
        long startTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            ScaleWeight weight = readScaleWeight();

            if (weight == null) {
                continue;
            }

            checkWeightErrors(weight);

            if (weight.status.isStable()) {
                if (weight.weight == 0 && !zeroValid) {
                    // Пропускаем
                } else {
                    return weight.weight;
                }
            }

            if (System.currentTimeMillis() > startTime + timeout) {
                return weight.weight;
            }

            Thread.sleep(10);
        }

        throw new InterruptedException("Thread interrupted while reading weight");
    }

    private void checkWeightErrors(ScaleWeight weight) throws JposException {
        if (weight.status.isOverweight()) {
            throw new JposException(JPOS_E_EXTENDED,
                    ScaleConst.JPOS_ESCAL_OVERWEIGHT,
                    "Weight exceeds maximum");
        }

        if (weight.weight < 0) {
            throw new JposException(JPOS_E_EXTENDED,
                    ScaleConst.JPOS_ESCAL_UNDER_ZERO,
                    "Weight below zero");
        }

    }

    // ======================== УПРАВЛЕНИЕ ПОТОКАМИ ========================
    private void startPollThread() {
        if (pollEnabled) {
            stopPollThread();
            pollThread = new Thread(new PollTarget(this), "ScalePollThread");
            pollThread.setDaemon(true);
            pollThread.start();
            logger.debug("Poll thread started");
        }
    }

    private void stopPollThread() {
        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(THREAD_STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                logger.error("Error stopping pollThread", e);
                Thread.currentThread().interrupt();
            }
            pollThread = null;
            logger.debug("Poll thread stopped");
        }
    }

    private void startEventThread() {
        stopEventThread();
        eventThread = new Thread(new EventTarget(this), "ScaleEventThread");
        eventThread.setDaemon(true);
        eventThread.start();
        logger.debug("Event thread started");
    }

    private void stopEventThread() {
        if (eventThread != null) {
            eventThread.interrupt();
            try {
                eventThread.join(THREAD_STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                logger.error("Error stopping eventThread", e);
                Thread.currentThread().interrupt();
            }
            eventThread = null;
            logger.debug("Event thread stopped");
        }
    }

    private void startWeightThread() {
        stopWeightThread();
        weightThread = new Thread(new WeightTarget(this), "ScaleWeightThread");
        weightThread.setDaemon(true);
        weightThread.start();
        logger.debug("Weight thread started");
    }

    private void stopWeightThread() {
        if (weightThread != null) {
            weightThread.interrupt();
            try {
                weightThread.join(THREAD_STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                logger.error("Error stopping weightThread", e);
                Thread.currentThread().interrupt();
            }
            weightThread = null;
            logger.debug("Weight thread stopped");
        }
    }

    private void stopAllThreads() {
        stopPollThread();
        stopEventThread();
        stopWeightThread();
    }

    // ======================== ОСНОВНЫЕ ПРОЦЕДУРЫ ПОТОКОВ ========================
    public void pollProc() {
        logger.debug("Poll thread started");
        try {
            while (!Thread.interrupted() && deviceEnabled) {
                readScaleWeight();
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            logger.debug("Poll thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Poll thread error", e);
        } finally {
            logger.debug("Poll thread stopped");
        }
    }

    public void eventProc() {
        logger.debug("Event thread started");
        try {
            while (!Thread.interrupted() && deviceEnabled) {
                JposEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                boolean canDeliver;

                if (event instanceof DataEvent) {
                    synchronized (this) {
                        canDeliver = dataEventEnabled && !freezeEvents;
                    }
                } else {
                    canDeliver = !freezeEvents;
                }

                if (canDeliver) {
                    fireJposEvent(event);

                    // Обработка ER_RETRY
                    if (event instanceof ErrorEvent) {
                        handleErrorResponse((ErrorEvent) event);
                    }

                    if (event instanceof DataEvent && autoDisable) {
                        handleAutoDisable();
                    }

                } else {
                    eventQueue.offer(event);
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            logger.debug("Event thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Event thread error", e);
        } finally {
            logger.debug("Event thread stopped");
        }
    }

    private void handleErrorResponse(ErrorEvent errorEvent) {
        int response = errorEvent.getErrorResponse();
        logger.debug("handleErrorResponse: response=" + response);

        if (response == JPOS_ER_RETRY) {
            logger.debug("ER_RETRY received");

            if (currentRequest != null && currentRequest.getRetryCount() < MAX_RETRY_COUNT) {
                WeightRequest retryRequest = currentRequest.copy();
                logger.debug("Retry #" + retryRequest.getRetryCount() + "/" + MAX_RETRY_COUNT
                        + " for request #" + currentRequest.getId());

                requestQueue.offer(retryRequest);
                setState(JPOS_S_BUSY);
            } else {
                logger.debug("Max retries exceeded or no current request");
                setState(JPOS_S_IDLE);
                currentRequest = null;
            }
        } else if (response == JPOS_ER_CLEAR) {
            logger.debug("ER_CLEAR received");
            setState(JPOS_S_IDLE);
            currentRequest = null;
        }
    }

    public void weightProc() {
        logger.debug("Weight thread started");
        try {
            while (!Thread.interrupted() && deviceEnabled && asyncMode) {
                try {
                    WeightRequest request = requestQueue.take();
                    currentRequest = request;
                    logger.debug("Processing request #" + request.getId()
                            + ", timeout=" + request.getTimeout()
                            + ", retry=" + request.getRetryCount());

                    processWeightRequest(request);
                } catch (InterruptedException e) {
                    logger.debug("Weight thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing weight request", e);
                }
            }
        } finally {
            currentRequest = null;
            logger.debug("Weight thread stopped");
        }
    }

    private void processWeightRequest(WeightRequest request) {
        try {
            long weight = readWeightAsync(request.getTimeout());
            DataEvent dataEvent = new DataEvent(this, (int) weight);
            addEvent(dataEvent);
            setState(JPOS_S_IDLE);
            currentRequest = null;
            logger.debug("Weight request processed, weight=" + weight);

        } catch (JposException e) {
            handleWeightError(e, request);
        } catch (InterruptedException e) {
            logger.debug("Weight request interrupted");
            Thread.currentThread().interrupt();
            setState(JPOS_S_IDLE);
            currentRequest = null;
        }
    }

    private void handleWeightError(JposException e, WeightRequest request) {
        boolean canRetry = isRetryableError(e) && request.getRetryCount() < MAX_RETRY_COUNT;

        ErrorEvent errorEvent = new ErrorEvent(
                this,
                e.getErrorCode(),
                e.getErrorCodeExtended(),
                JPOS_EL_INPUT,
                canRetry ? JPOS_ER_RETRY : JPOS_ER_CLEAR
        );

        setState(JPOS_S_ERROR);
        addEvent(errorEvent);
        logger.debug("Error event queued for request #" + request.getId()
                + ", canRetry=" + canRetry
                + ", errorCode=" + e.getErrorCode());
    }

    private boolean isRetryableError(JposException e) {
        if (e.getErrorCode() != JPOS_E_EXTENDED) {
            return false;
        }

        switch (e.getErrorCodeExtended()) {
            case ScaleConst.JPOS_ESCAL_OVERWEIGHT:
            case ScaleConst.JPOS_ESCAL_UNDER_ZERO:
                return true;
            default:
                return false;
        }
    }

    // ======================== ЧТЕНИЕ ВЕСА ========================
    private ScaleWeight readScaleWeight() throws JposException {
        try {
            if (scale == null) {
                return null;
            }

            ScaleWeight weight = scale.getWeight();

            if (weight == null || weight.status == null) {
                return null;
            }

            scaleLiveWeight = weight.status.isStable() ? weight.weight : 0;
            generateStatusEvents(weight);
            currentWeight = weight;

            return weight;

        } catch (Exception e) {
            logger.error("Error reading scale weight", e);
            throw getJposException(e);
        }
    }

    private void generateStatusEvents(ScaleWeight newWeight) {
        if (statusNotify != SCAL_SN_ENABLED) {
            return;
        }

        ScaleWeight oldWeight = currentWeight;

        if (oldWeight == null || newWeight.status.isStable() != oldWeight.status.isStable()) {
            statusUpdateEvent(newWeight.status.isStable()
                    ? SCAL_SUE_STABLE_WEIGHT : SCAL_SUE_WEIGHT_UNSTABLE);
        }

        if (newWeight.weight == 0 && (oldWeight == null || oldWeight.weight != 0)) {
            statusUpdateEvent(SCAL_SUE_WEIGHT_ZERO);
        }

        if (newWeight.weight < 0 && (oldWeight == null || oldWeight.weight >= 0)) {
            statusUpdateEvent(SCAL_SUE_WEIGHT_UNDER_ZERO);
        }

        if (newWeight.status.isOverweight()
                && (oldWeight == null || !oldWeight.status.isOverweight())) {
            statusUpdateEvent(SCAL_SUE_WEIGHT_OVERWEIGHT);
        }
    }

    private void handleAutoDisable() {
        try {
            logger.debug("AutoDisable: disabling device after DataEvent");
            setDeviceEnabled(false);
        } catch (JposException e) {
            logger.error("AutoDisable failed", e);
        }
    }

    // ======================== УПРАВЛЕНИЕ СОБЫТИЯМИ ========================
    private void addEvent(JposEvent event) {
        eventQueue.offer(event);
    }

    private void fireJposEvent(JposEvent event) {
        if (eventsCallback == null) {
            return;
        }

        logger.debug("fireJposEvent: " + event.getClass().getSimpleName());

        if (event instanceof StatusUpdateEvent) {
            eventsCallback.fireStatusUpdateEvent((StatusUpdateEvent) event);
        } else if (event instanceof DataEvent) {
            eventsCallback.fireDataEvent((DataEvent) event);
        } else if (event instanceof ErrorEvent) {
            eventsCallback.fireErrorEvent((ErrorEvent) event);
        } else if (event instanceof DirectIOEvent) {
            eventsCallback.fireDirectIOEvent((DirectIOEvent) event);
        } else if (event instanceof OutputCompleteEvent) {
            eventsCallback.fireOutputCompleteEvent((OutputCompleteEvent) event);
        }
    }

    private void statusUpdateEvent(int status) {
        logger.debug("statusUpdateEvent(" + status + ")");

        if (status >= SCL_SUE_STABLE_WEIGHT && status <= SCAL_SUE_WEIGHT_UNDER_ZERO) {
            if (statusNotify == SCAL_SN_ENABLED) {
                addEvent(new StatusUpdateEvent(this, status));
            }
        } else {
            addEvent(new StatusUpdateEvent(this, status));
        }
    }

    // ======================== УПРАВЛЕНИЕ ПИТАНИЕМ ========================
    public void setPowerState(int newPowerState) {
        if (powerNotify == JPOS_PN_ENABLED && newPowerState != this.powerState) {
            switch (newPowerState) {
                case JPOS_PS_ONLINE:
                    statusUpdateEvent(JPOS_SUE_POWER_ONLINE);
                    break;
                case JPOS_PS_OFF:
                    statusUpdateEvent(JPOS_SUE_POWER_OFF);
                    break;
                case JPOS_PS_OFFLINE:
                    statusUpdateEvent(JPOS_SUE_POWER_OFFLINE);
                    break;
                case JPOS_PS_OFF_OFFLINE:
                    statusUpdateEvent(JPOS_SUE_POWER_OFF_OFFLINE);
                    break;
            }
        }
        this.powerState = newPowerState;
    }

    // ======================== ФАБРИЧНЫЕ МЕТОДЫ ========================
    protected ScaleSerial createProtocol(String protocol) throws Exception {
        logger.debug("createProtocol(" + protocol + ")");

        if (protocol.equalsIgnoreCase("pos2")) {
            return new Pos2Serial();
        }
        if (protocol.equalsIgnoreCase("shtrih5")) {
            return new Shtrih5Serial();
        }
        if (protocol.equalsIgnoreCase("shtrih6")) {
            return new Shtrih6Serial();
        }
        throw new JposException(JPOS_E_FAILURE, "Unknown scale protocol: " + protocol);
    }

    // ======================== ОБРАБОТКА ИСКЛЮЧЕНИЙ ========================
    private JposException getJposException(Exception e) {
        logger.error("Exception caught", e);

        if (e instanceof JposException) {
            return (JposException) e;
        }

        if (e instanceof DeviceError) {
            DeviceError deviceError = (DeviceError) e;
            switch (deviceError.getCode()) {
                case IDevice.ERROR_NOLINK:
                    setPowerState(JPOS_PS_OFF_OFFLINE);
                    return new JposException(JPOS_E_OFFLINE, e.getMessage());
                default:
                    return new JposException(JPOS_E_FAILURE, e.getMessage());
            }
        }

        return new JposException(JPOS_E_FAILURE, e.getMessage());
    }

    // ======================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ========================
    public void setPollEnabled(boolean pollEnabled) {
        logger.debug("setPollEnabled(" + pollEnabled + ")");
        this.pollEnabled = pollEnabled;
        if (deviceEnabled && pollEnabled) {
            startPollThread();
        }
        logger.debug("setPollEnabled: OK");
    }

    public boolean getPollEnabled() {
        logger.debug("getPollEnabled");
        logger.debug("getPollEnabled: " + pollEnabled);
        return pollEnabled;
    }
}