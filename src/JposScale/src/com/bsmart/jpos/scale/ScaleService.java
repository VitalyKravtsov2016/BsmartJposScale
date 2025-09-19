package com.bsmart.jpos.scale;

import java.util.List;
import java.util.Vector;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
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

public class ScaleService extends Scale implements ScaleService113, ScaleConst, JposConst, JposEntryConst {

    /**
     *
     */
    private static final long serialVersionUID = 6309237509625068100L;
    private final Logger logger = Logger.getLogger(ScaleService.class);
    private final int S_CLOSED = 0;
    private final int S_OPENED = 1;
    private final int S_CLAIMED = 2;
    private final int S_ENABLED = 3;
    
    private boolean zeroValid = false;
    private int statusNotify = SCAL_SN_DISABLED;
    private int powerNotify = JPOS_PN_ENABLED;
    private int powerState = JPOS_PS_UNKNOWN;
    private ScaleSerial scale = null;
    private DeviceMetrics deviceMetrics;
    private int state = S_CLOSED;
    private boolean asyncMode = false;
    private EventCallbacks eventsCallback = null;
    private String m_logicalName = null;
    private boolean dataEventEnabled = false;
    private boolean freezeEvents = false;
    private boolean autoDisable = false;
    private int tareWeight = 0;
    private ScaleWeight m_weight = null;
    private Thread eventThread = null;
    private Thread pollThread = null;
    private Thread weightThread = null;
    private long scaleLiveWeight = 0;
    private final List<JposEvent> events = new ArrayList<JposEvent>();
    private final List<WeightRequest> requests = new ArrayList<WeightRequest>();

    public boolean getCapCompareFirmwareVersion() throws JposException {
        logger.debug("getCapCompareFirmwareVersion()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapCompareFirmwareVersion = " + String.valueOf(result));
        return result;
    }

    public boolean getCapStatusUpdate() throws JposException {
        logger.debug("getCapStatusUpdate()");
        boolean result = true;
        checkOpened();
        logger.debug("getCapStatusUpdate = " + String.valueOf(result));
        return result;
    }

    public boolean getCapUpdateFirmware() throws JposException {
        logger.debug("getCapUpdateFirmware()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapUpdateFirmware = " + String.valueOf(result));
        return result;
    }

    public boolean getCapDisplay() throws JposException {
        logger.debug("getCapDisplay()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapDisplay = " + String.valueOf(result));
        return result;
    }

    public boolean getCapStatisticsReporting() throws JposException {
        logger.debug("getCapStatisticsReporting()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapStatisticsReporting = " + String.valueOf(result));
        return result;
    }

    public boolean getCapUpdateStatistics() throws JposException {
        logger.debug("getCapUpdateStatistics()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapUpdateStatistics = " + String.valueOf(result));
        return result;
    }

    public boolean getCapDisplayText() throws JposException {
        logger.debug("getCapDisplayText()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapDisplayText = " + String.valueOf(result));
        return result;
    }

    public int getCapPowerReporting() throws JposException {
        logger.debug("getCapPowerReporting()");
        int result = JPOS_PR_STANDARD;
        checkOpened();
        logger.debug("getCapPowerReporting = " + JposUtils.getCapPowerReportingText(result));
        return result;
    }

    public boolean getCapPriceCalculating() throws JposException {
        logger.debug("getCapPriceCalculating()");
        boolean result = false;
        checkOpened();
        logger.debug("getCapPriceCalculating = " + String.valueOf(result));
        return result;
    }

    public boolean getCapTareWeight() throws JposException {
        logger.debug("getCapTareWeight()");
        boolean result = true;
        checkOpened();
        logger.debug("getCapTareWeight = " + String.valueOf(result));
        return result;
    }

    public boolean getCapZeroScale() throws JposException {
        logger.debug("getCapZeroScale()");

        checkOpened();
        boolean result = false;
        if (scale.getType() == EScale.Pos2) {
            result = true;
        }
        logger.debug("getCapZeroScale = " + String.valueOf(result));
        return result;
    }

    public void open(String logicalName, EventCallbacks eventsCallback) throws JposException {
        logger.debug("open(" + logicalName + ", " + eventsCallback + ")");
        if (state > S_OPENED) {
            logger.warn("state > S_OPENED");
            // throw new JposException(JPOS_E_CLAIMED);
            return;
        }

        state = S_CLOSED;
        this.eventsCallback = eventsCallback;
        m_logicalName = logicalName;
        asyncMode = false;

        JposPropertyReader reader = new JposPropertyReader(m_jposEntry);
        try {
            String protocol = reader.readString("protocol").toLowerCase();
            scale = createProtocol(protocol);

            StringParams params = new StringParams();
            params.set(IDevice.PARAM_DATABITS, "8");
            params.set(IDevice.PARAM_STOPBITS, "1");
            params.set(IDevice.PARAM_PARITY, "0");

            String value = reader.readString(RS232Const.RS232_PORT_NAME_PROP_NAME, "");
            params.set(IDevice.PARAM_PORTNAME, value);

            value = reader.readString(RS232Const.RS232_BAUD_RATE_PROP_NAME, "9600");
            params.set(IDevice.PARAM_BAUDRATE, value);

            value = reader.readString("password", "30");
            params.set(IDevice.PARAM_PASSWORD, value);

            value = reader.readString("timeout", "100");
            params.set(IDevice.PARAM_OPEN_TIMEOUT, value);

            value = reader.readString("portType", "0");
            params.set(IDevice.PARAM_PORTTYPE, value);

            scale.setParams(params);
            state = S_OPENED;
            logger.debug("open: OK");
        } catch (Exception e) {
            throw getJposException(e);
        }
    }

    private ScaleSerial createProtocol(String protocol) throws Exception {
        if (protocol.equalsIgnoreCase("pos2")) {
            return new Pos2Serial();
        }
        if (protocol.equalsIgnoreCase("shtrih5")) {
            return new Shtrih5Serial();
        }
        if (protocol.equalsIgnoreCase(
                "shtrih6")) {
            return new Shtrih6Serial();
        }
        throw new JposException(JPOS_E_FAILURE, "Неизвестный протокол весов '" + protocol + "'");
    }

    public void release() throws JposException {
        logger.debug("release()");
        checkClaimed();
        try {
            scale.disconnect();
            state = S_OPENED;
            logger.debug("release: OK");
        } catch (Exception e) {
            logger.error("release: ", e);
            throw getJposException(e);
        }
    }

    public void claim(int timeout) throws JposException {
        logger.debug("claim(" + String.valueOf(timeout) + ")");
        checkOpened();
        if (state >= S_CLAIMED) {
            logger.warn("state >= S_CLAIMED");
            // throw new JposException(JPOS_E_CLAIMED);
            return;
        }

        try {
            scale.connect();
            deviceMetrics = scale.getDeviceMetrics();
            logger.debug(deviceMetrics.toString());
            
        } catch (Exception e) {
            throw getJposException(e);
        }
        state = S_CLAIMED;
        logger.debug("claim: OK");
    }

    public void close() throws JposException {
        logger.debug("close()");
        if (state >= S_CLAIMED) {
            release();
        }
        asyncMode = false;
        state = S_CLOSED;
        scale = null;
        logger.debug("close: OK");
    }

    public void compareFirmwareVersion(String arg0, int[] arg1) throws JposException {
        logger.debug("compareFirmwareVersion("
                + String.valueOf(arg0) + ", "
                + String.valueOf(arg1) + ")");

        logger.debug("compareFirmwareVersion: OK");
    }

    public int getScaleLiveWeight() throws JposException {
        logger.debug("getScaleLiveWeight()");
        logger.debug("getScaleLiveWeight = " + scaleLiveWeight);
        return (int) scaleLiveWeight;
    }

    public int getStatusNotify() throws JposException {
        logger.debug("getStatusNotify()");
        logger.debug("getStatusNotify = " + statusNotify);
        return statusNotify;
    }

    public void setStatusNotify(int statusNotify) throws JposException {
        logger.debug("setStatusNotify(" + String.valueOf(statusNotify) + ")");
        this.statusNotify = statusNotify;
        logger.debug("setStatusNotify: OK");
    }

    public void updateFirmware(String arg0) throws JposException {
        logger.debug("updateFirmware(" + String.valueOf(arg0) + ")");
        logger.debug("updateFirmware: OK");
    }

    public void resetStatistics(String arg0) throws JposException {
        logger.debug("resetStatistics(" + String.valueOf(arg0) + ")");
        logger.debug("resetStatistics: OK");
    }

    public void retrieveStatistics(String[] arg0) throws JposException {
        logger.debug("retrieveStatistics(" + String.valueOf(arg0) + ")");
        logger.debug("retrieveStatistics: OK");
    }

    public void updateStatistics(String arg0) throws JposException {
        logger.debug("updateStatistics(" + String.valueOf(arg0) + ")");
        logger.debug("updateStatistics: OK");
    }

    public void clearInput() throws JposException {
        logger.debug("clearInput()");
        synchronized (events) {
            events.clear();
        }
        logger.debug("clearInput: OK");
    }

    public void displayText(String arg0) throws JposException {
        logger.debug("displayText(" + arg0 + ")");
        logger.debug("displayText: OK");
    }

    public void setAsyncMode(boolean async) throws JposException {
        logger.debug("setAsyncMode(" + String.valueOf(async) + ")");

        checkOpened();
        if (async == this.asyncMode) {
            return;
        }

        this.asyncMode = async;
        if (this.asyncMode) {
            weightThread = new Thread(new WeightTarget(this));
            weightThread.start();
        } else {
            weightThread.interrupt();
            try {
                weightThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
            weightThread = null;
        }
        logger.debug("setAsyncMode: OK");
    }

    public boolean getAsyncMode() throws JposException {
        logger.debug("getAsyncMode()");
        logger.debug("getAsyncMode = " + String.valueOf(asyncMode));
        return asyncMode;
    }

    public int getDataCount() throws JposException {
        logger.debug("getDataCount()");
        logger.debug("getDataCount = 0");
        return 0;
    }

    public int getMaxDisplayTextChars() throws JposException {
        logger.debug("getMaxDisplayTextChars()");
        checkOpened();
        logger.debug("getMaxDisplayTextChars = 0");
        return 0;
    }

    public int getPowerNotify() throws JposException {
        logger.debug("getPowerNotify()");
        checkOpened();
        logger.debug("getPowerNotify = JPOS_PN_DISABLED");
        return JPOS_PN_DISABLED;
    }

    public int getPowerState() throws JposException {
        logger.debug("getPowerState()");
        checkOpened();
        logger.debug("getPowerState = JPOS_PS_UNKNOWN");
        return powerState;
    }

    public long getSalesPrice() throws JposException {
        logger.debug("getSalesPrice()");
        checkOpened();
        logger.debug("getSalesPrice = 0");
        return 0;
    }

    public int getTareWeight() throws JposException {
        logger.debug("getTareWeight()");
        checkOpened();
        logger.debug("getTareWeight = " + String.valueOf(tareWeight));
        return tareWeight;
    }

    public long getUnitPrice() throws JposException {
        logger.debug("getUnitPrice()");
        checkOpened();
        logger.debug("getUnitPrice = 0");
        return 0;
    }

    public boolean getAutoDisable() throws JposException {
        logger.debug("getAutoDisable()");
        logger.debug("getAutoDisable = " + String.valueOf(autoDisable));
        return autoDisable;
    }

    public void setAutoDisable(boolean autoDisable) throws JposException {
        logger.debug("setAutoDisable(" + String.valueOf(autoDisable) + ")");
        this.autoDisable = autoDisable;
        logger.debug("setAutoDisable: OK");
    }

    public void setDataEventEnabled(boolean enabled) throws JposException {
        logger.debug("setDataEventEnabled(" + String.valueOf(enabled) + ")");
        dataEventEnabled = enabled;
        if (dataEventEnabled){
            events.notifyAll();
        }
        logger.debug("setDataEventEnabled: OK");
    }

    public boolean getDataEventEnabled() throws JposException {
        logger.debug("getDataEventEnabled()");
        logger.debug("getDataEventEnabled = " + dataEventEnabled);
        return dataEventEnabled;
    }

    public void setPowerNotify(int powerNotify) throws JposException {
        logger.debug("setPowerNotify(" + powerNotify + ")");
        this.powerNotify = powerNotify;
        logger.debug("setPowerNotify: OK");
    }

    public void setTareWeight(int tareWeight) throws JposException {
        logger.debug("setTareWeight(" + tareWeight + ")");
        checkEnabled();

        try {
            this.tareWeight = tareWeight;
            scale.tara((long) tareWeight);
        } catch (Exception e) {
            throw getJposException(e);
        }

        logger.debug("setTareWeight: OK");
    }

    public void setUnitPrice(long arg0) throws JposException {
        logger.debug("setUnitPrice(" + arg0 + ")");
        logger.debug("setUnitPrice: JPOS_E_ILLEGAL");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void zeroScale() throws JposException {
        logger.debug("zeroScale()");
        checkEnabled();
        try {
            scale.zero();
        } catch (Exception e) {
            throw getJposException(e);
        }
        logger.debug("zeroScale: OK");
    }

    public int getMaximumWeight() throws JposException {
        logger.debug("getMaximumWeight()");
        logger.debug("getMaximumWeight = 2147483647");
        return 2147483647;
    }

    public int getWeightUnit() throws JposException {
        logger.debug("getWeightUnit()");
        checkOpened();
        logger.debug("getWeightUnit() = SCAL_WU_GRAM");
        return ScaleConst.SCAL_WU_GRAM;
    }

    public void readWeight(int[] data, int timeout) throws JposException {
        logger.debug("readWeight(" + data + ", " + timeout + ")");

        checkEnabled();
        try {
            if (asyncMode) {
                synchronized (requests) {
                    requests.add(new WeightRequest(timeout));
                    requests.notifyAll();
                }
                return;
            }
            data[0] = (int) readWeightTimeout(timeout);
        } catch (Exception e) {
            throw getJposException(e);
        }
        logger.debug("readWeight: OK");
    }

    public void checkHealth(int arg0) throws JposException {
        logger.debug("checkHealth(" + arg0 + ")");
        logger.debug("checkHealth = JPOS_E_ILLEGAL");
        throw new JposException(JPOS_E_ILLEGAL, "Неподдерживается");
    }

    public void directIO(int arg0, int[] arg1, Object arg2) throws JposException {
        logger.debug("directIO(" + arg0 + ", " + arg1 + ", " + arg2 + ")");
        logger.debug("directIO = JPOS_E_ILLEGAL");
        throw new JposException(JPOS_E_ILLEGAL, "Неизвестная команда");
    }

    public String getCheckHealthText() throws JposException {
        logger.debug("getCheckHealthText()");
        logger.debug("getCheckHealthText = ");
        return "";
    }

    public boolean getClaimed() throws JposException {
        logger.debug("getClaimed()");
        logger.debug("getClaimed = " + (state >= S_CLAIMED));
        return (state >= S_CLAIMED);
    }

    public String getDeviceServiceDescription() throws JposException {
        logger.debug("getDeviceServiceDescription()");
        logger.debug("getDeviceServiceDescription = ScalePos2Service");
        return "ScalePos2Service";
    }

    public int getDeviceServiceVersion() throws JposException {
        // 001 | 000 | 000
        int version = 1013000 + ServiceVersionUtil.getVersionInt();
        logger.debug("getDeviceServiceVersion()");
        logger.debug("getDeviceServiceVersion = " + version);
        return version;
    }

    public boolean getFreezeEvents() throws JposException {
        logger.debug("getFreezeEvents()");
        logger.debug("getFreezeEvents = " + freezeEvents);
        return freezeEvents;
    }

    public void setFreezeEvents(boolean freezeEvents) throws JposException {
        logger.debug("setFreezeEvents(" + freezeEvents + ")");
        checkOpened();
        if (this.freezeEvents != freezeEvents) {
            this.freezeEvents = freezeEvents;
            if (freezeEvents) {
                if (eventThread != null) 
                {
                    eventThread.interrupt();
                    synchronized (events) {
                        events.notifyAll();
                    }
                    try{
                        eventThread.join();
                    }catch(Exception e){
                        logger.error(e);
                    }
                    eventThread = null;
                }
            } else {
                eventThread = new Thread(new EventTarget(this));
                eventThread.start();
            }
        }
        logger.debug("setFreezeEvents: OK");
    }

    class EventTarget implements Runnable {

        private final ScaleService service;

        public EventTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.eventProc();
        }
    }

    class PollTarget implements Runnable {

        private final ScaleService service;

        public PollTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.pollProc();
        }
    }

    public void eventProc() {
        try {
            while (!Thread.interrupted()) 
            {
                synchronized (events) 
                {
                    int index = 0;
                    while (index < events.size())
                    {
                        JposEvent event = (JposEvent) events.get(index);
                        if (!(event instanceof DataEvent)|| dataEventEnabled)
                        {
                            events.remove(index);
                            fireJposEvent(event);
                        } else{
                            index++;
                        }
                    }
                }
                events.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fireJposEvent(JposEvent event) 
    {
        if (eventsCallback == null) return;
        if (event instanceof StatusUpdateEvent) {
            eventsCallback.fireStatusUpdateEvent((StatusUpdateEvent) event);
        }
        if (event instanceof DataEvent) {
            eventsCallback.fireDataEvent((DataEvent) event);
        }
        if (event instanceof DirectIOEvent) {
            eventsCallback.fireDirectIOEvent((DirectIOEvent) event);
        }
        if (event instanceof ErrorEvent) {
            eventsCallback.fireErrorEvent((ErrorEvent) event);
        }
        if (event instanceof OutputCompleteEvent) {
            eventsCallback.fireOutputCompleteEvent((OutputCompleteEvent) event);
        }
    }

    private void addEvent(JposEvent event) {
        synchronized (events) {
            events.add(event);
            events.notifyAll();
        }
    }

    private void statusUpdateEvent(int value) {
        logger.debug("statusUpdateEvent(" + value + ")");
        if ((value >= SCL_SUE_STABLE_WEIGHT) && (value <= SCAL_SUE_WEIGHT_UNDER_ZERO)) {
            if (statusNotify == SCAL_SN_ENABLED) {
                addEvent(new StatusUpdateEvent(this, value));
            }
        } else {
            addEvent(new StatusUpdateEvent(this, value));
        }
    }

    public String getPhysicalDeviceDescription() throws JposException {
        logger.debug("getPhysicalDeviceDescription()");
        checkOpened();
        switch (scale.getType()) {
            case Pos2:
                /*
                 * logger.debug(
                 * "<--getPhysicalDeviceDescription() Весы ШТРИХ-М POS2");
                 */ return "Весы ШТРИХ-М POS2";
            case Shtrih5:
                /*
                 * logger.debug(
                 * "<--getPhysicalDeviceDescription() Весы ШТРИХ-М ШТРИХ5");
                 */ return "Весы ШТРИХ-М ШТРИХ5";
            case Shtrih6:
                /*
                 * logger.debug(
                 * "<--getPhysicalDeviceDescription() Весы ШТРИХ-М ШТРИХ6");
                 */ return "Весы ШТРИХ-М ШТРИХ6";
        }
        logger.debug("getPhysicalDeviceDescription = Весы ШТРИХ-М");
        return "Весы ШТРИХ-М";
    }

    public String getPhysicalDeviceName() throws JposException {
        logger.debug("getPhysicalDeviceName()");
        checkOpened();
        String s = getPhysicalDeviceDescription();
        logger.debug("getPhysicalDeviceName = " + s);
        return s;
    }

    public int getState() throws JposException {
        logger.debug("getState()");

        int result = JPOS_S_ERROR;

        if (asyncMode && weightThread.isAlive()) {
            result = JPOS_S_BUSY;
        }

        switch (state) {
            case S_CLOSED:
                result = JPOS_S_CLOSED;
                break;
                
            case S_OPENED:
            case S_CLAIMED:
            case S_ENABLED:
                result = JPOS_S_IDLE;
                break;
                
            default:
                result = JPOS_S_ERROR;
        }
        logger.debug("getState = " + JposUtils.getStateText(result));
        return result;
    }

    public boolean getDeviceEnabled() throws JposException {
        logger.debug("getDeviceEnabled()");
        logger.debug("getDeviceEnabled = " + (state >= S_ENABLED));
        return (state >= S_ENABLED);
    }

    public void setDeviceEnabled(boolean enabled) throws JposException {
        logger.debug("setDeviceEnabled(" + enabled + ")");
        checkClaimed();
        state = enabled ? S_ENABLED : S_CLAIMED;
        try {
            if (enabled) {
                readScaleWeight();
                setPowerState(JPOS_PS_ONLINE);
                pollThread = new Thread(new PollTarget(this));
                pollThread.start();
            } else {
                setPowerState(JPOS_PS_UNKNOWN);
                pollThread.interrupt();
                pollThread.join();
                pollThread = null;
            }
        } catch (Exception e) {
            throw getJposException(e);
        }
        logger.debug("setDeviceEnabled: OK");
    }

    public void deleteInstance() throws JposException {
        logger.debug("deleteInstance()");
    }

    public boolean getZeroValid() throws JposException {
        logger.debug("getZeroValid()");
        logger.debug("getZeroValid = " + zeroValid);
        return zeroValid;
    }

    public void setZeroValid(boolean zeroValid) throws JposException {
        logger.debug("setZeroValid(" + zeroValid + ")");
        this.zeroValid = zeroValid;
        logger.debug("setZeroValid: OK");
    }

    private void handleErrorEvent(ErrorEvent event) {
        logger.debug("handleErrorEvent(" + event + ")");
        eventsCallback.fireErrorEvent(event);
        logger.debug("handleErrorEvent: OK");
    }

    private void checkOpened() throws JposException {
        if (state < S_OPENED) {
            logger.debug("checkOpened() JPOS_E_CLOSED");
            throw new JposException(JPOS_E_CLOSED);
        }
    }

    private void checkClaimed() throws JposException {
        if (state < S_CLAIMED) {
            logger.debug("checkOpened = JPOS_E_NOTCLAIMED");
            throw new JposException(JPOS_E_NOTCLAIMED);
        }
    }

    private void checkEnabled() throws JposException {
        if (state < S_ENABLED) {
            logger.debug("checkOpened() JPOS_E_DISABLED");
            throw new JposException(JPOS_E_DISABLED);
        }
    }

    public void pollProc() {
        int pollInterval = 100;
        try {
            logger.debug("Poll thread start");
            while (!Thread.interrupted()) {
                readScaleWeight();
                Thread.sleep(pollInterval);
            }
            logger.debug("Poll thread stop");
        } catch (Exception e) {
            logger.error("Poll proc, ", e);
        }
    }

    private ScaleWeight readScaleWeight() throws JposException {
        try {
            ScaleWeight weight = scale.getWeight();
            if (weight == null) {
                return null;
            }

            if (weight.status.isStable()) {
                scaleLiveWeight = weight.weight;
            }

            if (weight.status.isStable() && ((m_weight == null) || (m_weight.status.isStable()))) {
                if (weight.status.isStable()) {
                    statusUpdateEvent(SCAL_SUE_STABLE_WEIGHT);
                } else {
                    statusUpdateEvent(SCAL_SUE_WEIGHT_UNSTABLE);
                }
            }

            if ((weight.weight == 0) && ((m_weight == null) || (m_weight.weight != 0))) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_ZERO);
            }

            if ((weight.weight < 0) && ((m_weight == null) || (m_weight.weight >= 0))) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_UNDER_ZERO);
            }

            if (weight.status.isOverweight() && ((m_weight == null) || (!m_weight.status.isOverweight()))) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_OVERWEIGHT);
            }
            m_weight = weight;
            return weight;

        } catch (Exception e) {
            throw getJposException(e);
        }
    }

    public long readWeightTimeout(int timeout) throws JposException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (true) {
            ScaleWeight weight = readScaleWeight();
            if (weight == null) {
                return 0;
            }

            if (weight.status.isStable()) {
                // Test for overweight
                if (weight.status.isOverweight()) {
                    throw new JposException(JPOS_E_EXTENDED,
                            ScaleConst.JPOS_ESCAL_OVERWEIGHT,
                            "Вес превышает максимальный");
                }
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

            if (System.currentTimeMillis() > (startTime + timeout)) {
                throw new JposException(JPOS_E_TIMEOUT, "Истекло время ожидания фиксированного веса");
            }
            Thread.sleep(100);
        }
    }

    class WeightTarget implements Runnable {

        private final ScaleService service;

        public WeightTarget(ScaleService service) {
            this.service = service;
        }

        public void run() {
            service.weightProc();
        }
    }

    public void weightProc() {
        try {
            while (!Thread.interrupted()) {
                synchronized (requests) {
                    while (!requests.isEmpty()) {
                        WeightRequest request = requests.remove(0);
                        try {
                            long weight = readWeightTimeout(request.getTimeout());
                            DataEvent event = new DataEvent(this, (int) weight);
                            addEvent(event);

                        } catch (JposException e) {
                            ErrorEvent event = new ErrorEvent(this,
                                    e.getErrorCode(), e.getErrorCodeExtended(),
                                    JPOS_EL_INPUT, JPOS_ER_RETRY);
                            handleErrorEvent(event);
                        }
                    }
                    requests.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JposException getJposException(Exception e) {
        logger.error(e);
        if (e instanceof DeviceError) {
            DeviceError deviceError = (DeviceError) e;
            switch (deviceError.getCode()) {
                case IDevice.ERROR_NOLINK: {
                    setPowerState(JPOS_PS_OFF_OFFLINE);
                    return new JposException(JPOS_E_TIMEOUT, e.getMessage());
                }
                default: {
                    return new JposException(JposConst.JPOS_E_FAILURE, e.getMessage());
                }
            }
        }
        return new JposException(JposConst.JPOS_E_FAILURE, e.getMessage());
    }

    public void setPowerState(int powerState) {
        if (powerNotify == JPOS_PN_ENABLED) {
            if (powerState != this.powerState) {
                switch (powerState) {
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
        }
        this.powerState = powerState;
    }

}
