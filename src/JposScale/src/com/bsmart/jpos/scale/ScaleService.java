package com.bsmart.jpos.scale;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

public class ScaleService extends Scale implements ScaleService113, ScaleConst, JposConst, JposEntryConst {

    /**
     *
     */
    private static final long serialVersionUID = 6309237509625068100L;
    private final Logger logger = LogManager.getLogger(ScaleService.class);
    private final int S_CLOSED = 0;
    private final int S_OPENED = 1;
    private final int S_CLAIMED = 2;
    private final int S_ENABLED = 3;
    private final int S_ERROR = 4; // Добавлено состояние ошибки

    private boolean zeroValid = false;
    private int statusNotify = SCAL_SN_DISABLED;
    private int powerNotify = JPOS_PN_DISABLED;
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
    private final BlockingQueue<JposEvent> eventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<WeightRequest> requests = new LinkedBlockingQueue<>();
    private boolean pollEnabled = true;

    // Флаг для отслеживания активной асинхронной операции
    private volatile boolean asyncOperationInProgress = false;

    // Храним текущий запрос для возможности повтора
    private volatile WeightRequest currentRequest = null;

    public void setPollEnabled(boolean pollEnabled) throws JposException {
        this.pollEnabled = pollEnabled;
    }

    public boolean getPollEnabled() throws JposException {
        return pollEnabled;
    }

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

    // === Новые capability для версии 1.14 (все false, так как не реализованы) ===
    public boolean getCapFreezeValue() throws JposException {
        logger.debug("getCapFreezeValue()");
        checkOpened();
        return false;
    }

    public boolean getCapReadLiveWeightWithTare() throws JposException {
        logger.debug("getCapReadLiveWeightWithTare()");
        checkOpened();
        return false;
    }

    public boolean getCapSetPriceCalculationMode() throws JposException {
        logger.debug("getCapSetPriceCalculationMode()");
        checkOpened();
        return false;
    }

    public boolean getCapSetUnitPriceWithWeightUnit() throws JposException {
        logger.debug("getCapSetUnitPriceWithWeightUnit()");
        checkOpened();
        return false;
    }

    public boolean getCapSpecialTare() throws JposException {
        logger.debug("getCapSpecialTare()");
        checkOpened();
        return false;
    }

    public boolean getCapTarePriority() throws JposException {
        logger.debug("getCapTarePriority()");
        checkOpened();
        return false;
    }

    public int getMinimumWeight() throws JposException {
        logger.debug("getMinimumWeight()");
        checkOpened();
        // Минимальный вес зависит от режима работы, возвращаем 0 по умолчанию
        return 0;
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

        StringParams params = new StringParams();
        params.set(IDevice.PARAM_PORTNAME, "");
        params.set(IDevice.PARAM_DATABITS, "8");
        params.set(IDevice.PARAM_STOPBITS, "1");
        params.set(IDevice.PARAM_PARITY, "0");
        params.set(IDevice.PARAM_PASSWORD, "30");
        params.set(IDevice.PARAM_OPEN_TIMEOUT, "100");
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

                value = reader.readString("timeout", "100");
                params.set(IDevice.PARAM_OPEN_TIMEOUT, value);

                value = reader.readString("portType", "0");
                params.set(IDevice.PARAM_PORTTYPE, value);
            }

            scale = createProtocol(protocol);
            scale.setParams(params);
            state = S_OPENED;
            logger.debug("open: OK");
        } catch (Exception e) {
            throw getJposException(e);
        }
    }

    protected ScaleSerial createProtocol(String protocol) throws Exception {
        if (protocol.equalsIgnoreCase("pos2")) {
            return new Pos2Serial();
        }
        if (protocol.equalsIgnoreCase("shtrih5")) {
            return new Shtrih5Serial();
        }
        if (protocol.equalsIgnoreCase("shtrih6")) {
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

        if (getDeviceEnabled()) {
            setDeviceEnabled(false);
        }
        // Выключаем устройство, только если оно захвачено и включено
        if (state >= S_CLAIMED) {
            release();
        }

        // Сбрасываем состояние
        asyncMode = false;
        asyncOperationInProgress = false;
        currentRequest = null;
        state = S_CLOSED;
        scale = null;
        m_weight = null;

        // Очищаем очереди
        requests.clear();
        eventQueue.clear();

        logger.debug("close: OK");
    }

    public void compareFirmwareVersion(String arg0, int[] arg1) throws JposException {
        logger.debug("compareFirmwareVersion(" + String.valueOf(arg0) + ", " + String.valueOf(arg1) + ")");

        // Должно выбрасывать исключение, если capability false
        if (!getCapCompareFirmwareVersion()) {
            throw new JposException(JPOS_E_ILLEGAL, "compareFirmwareVersion не поддерживается");
        }

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

        // Должно выбрасывать исключение, если capability false
        if (!getCapUpdateFirmware()) {
            throw new JposException(JPOS_E_ILLEGAL, "updateFirmware не поддерживается");
        }

        logger.debug("updateFirmware: OK");
    }

    public void resetStatistics(String arg0) throws JposException {
        logger.debug("resetStatistics(" + String.valueOf(arg0) + ")");

        // Должно выбрасывать исключение, если capability false
        if (!getCapStatisticsReporting() || !getCapUpdateStatistics()) {
            throw new JposException(JPOS_E_ILLEGAL, "resetStatistics не поддерживается");
        }

        logger.debug("resetStatistics: OK");
    }

    public void retrieveStatistics(String[] arg0) throws JposException {
        logger.debug("retrieveStatistics(" + String.valueOf(arg0) + ")");

        // Должно выбрасывать исключение, если capability false
        if (!getCapStatisticsReporting()) {
            throw new JposException(JPOS_E_ILLEGAL, "retrieveStatistics не поддерживается");
        }

        // В реальной реализации здесь нужно заполнить arg0[0] XML со статистикой
        // Для примера возвращаем пустой XML
        if (arg0 != null && arg0.length > 0) {
            arg0[0] = "<?xml version=\"1.0\"?><UPOSStat></UPOSStat>";
        }

        logger.debug("retrieveStatistics: OK");
    }

    public void updateStatistics(String arg0) throws JposException {
        logger.debug("updateStatistics(" + String.valueOf(arg0) + ")");

        // Должно выбрасывать исключение, если capability false
        if (!getCapStatisticsReporting() || !getCapUpdateStatistics()) {
            throw new JposException(JPOS_E_ILLEGAL, "updateStatistics не поддерживается");
        }

        logger.debug("updateStatistics: OK");
    }

    public void clearInput() throws JposException {
        logger.debug("clearInput()");

        // Очищаем очередь событий
        eventQueue.clear();
        // Очищаем очередь запросов
        requests.clear();
        // Сбрасываем текущий запрос
        currentRequest = null;
        // Сбрасываем флаг операции
        asyncOperationInProgress = false;
        // Сбрасываем состояние ошибки, если оно было
        if (state == S_ERROR) {
            state = getDeviceEnabled() ? S_ENABLED : S_CLAIMED;
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
            weightThread.setName("ScaleWeightThread");
            weightThread.start();
        } else {
            // Сбрасываем флаг операции при выходе из асинхронного режима
            asyncOperationInProgress = false;
            currentRequest = null;
            if (weightThread != null) {
                weightThread.interrupt();
                try {
                    weightThread.join(1000);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                    Thread.currentThread().interrupt();
                }
                weightThread = null;
            }
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
        int count = eventQueue.size();
        logger.debug("getDataCount = " + count);
        return count;
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
        logger.debug("getPowerNotify = " + powerNotify);
        return powerNotify;
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
        synchronized (this) {
            dataEventEnabled = enabled;
            // Пробуждаем поток обработки событий для проверки
            if (eventThread != null) {
                eventThread.interrupt(); // Прерываем сон, чтобы сразу проверить новые события
            }
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

        // Проверяем состояние ошибки
        if (state == S_ERROR) {
            throw new JposException(JPOS_E_FAILURE, "Устройство в состоянии ошибки. Вызовите clearInput()");
        }

        try {
            if (asyncMode) {
                // Проверяем, не выполняется ли уже асинхронная операция
                if (asyncOperationInProgress) {
                    throw new JposException(JPOS_E_BUSY, "Асинхронная операция уже выполняется");
                }
                asyncOperationInProgress = true;
                WeightRequest request = new WeightRequest(timeout);
                requests.offer(request);
                return;
            } else {
                data[0] = (int) readWeightTimeout(timeout);
            }
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
        // Уменьшаем версию до реально поддерживаемой (1.9 + ZeroValid из 1.13)
        // Формат: MMMnnnbbb (Major*1000000 + Minor*1000 + Build)
        int version = 1009000 + ServiceVersionUtil.getVersionInt(); // Заявляем версию 1.9
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

        this.freezeEvents = freezeEvents;

        // Запускаем поток обработки событий, если он еще не запущен
        if (eventThread == null) {
            eventThread = new Thread(new EventTarget(this));
            eventThread.setName("ScaleEventThread");
            eventThread.start();
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

    /**
     * Обработка autoDisable после отправки DataEvent Теперь вызывается
     * синхронно в потоке обработки событий
     */
    private void handleAutoDisableAfterDataEvent() {
        if (autoDisable) {
            try {
                logger.debug("AutoDisable: disabling device after DataEvent");
                setDeviceEnabled(false);
            } catch (JposException e) {
                logger.error("AutoDisable failed", e);
            }
        }
    }

    public void eventProc() {
        logger.debug("Event processing thread started");
        try {
            while (!Thread.interrupted()) {
                try {
                    // Берем событие из очереди, ждем до 100 мс
                    JposEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (event == null) {
                        continue; // Таймаут, проверяем флаги
                    }

                    // Проверяем, можно ли доставить событие
                    boolean canDeliver = true;

                    if (event instanceof DataEvent) {
                        synchronized (this) {
                            canDeliver = dataEventEnabled && !freezeEvents;
                        }
                    } else {
                        canDeliver = !freezeEvents;
                    }

                    if (canDeliver) {
                        fireJposEvent(event);

                        // Обработка autoDisable после отправки DataEvent
                        if (event instanceof DataEvent) {
                            handleAutoDisableAfterDataEvent();
                        }
                    } else {
                        // Если доставить нельзя, возвращаем событие в начало очереди
                        // Используем offer, чтобы не блокироваться
                        boolean returned = eventQueue.offer(event);
                        if (!returned) {
                            logger.error("Failed to return event to queue, event lost: " + event);
                        }

                        // Ждем немного, чтобы не крутиться в пустом цикле
                        Thread.sleep(10);
                    }

                } catch (InterruptedException e) {
                    logger.debug("Event thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("eventProc error", e);
        } finally {
            logger.debug("Event processing thread stopped");
        }
    }

    private void fireJposEvent(JposEvent event) {
        if (eventsCallback == null) {
            return;
        }

        logger.debug("fireJposEvent, " + event);
        if (event instanceof StatusUpdateEvent) {
            logger.debug("fireStatusUpdateEvent, " + event);
            eventsCallback.fireStatusUpdateEvent((StatusUpdateEvent) event);
        }
        if (event instanceof DataEvent) {
            logger.debug("fireDataEvent, " + event);
            eventsCallback.fireDataEvent((DataEvent) event);
        }
        if (event instanceof DirectIOEvent) {
            logger.debug("fireDirectIOEvent, " + event);
            eventsCallback.fireDirectIOEvent((DirectIOEvent) event);
        }
        if (event instanceof ErrorEvent) {
            logger.debug("fireErrorEvent, " + event);
            eventsCallback.fireErrorEvent((ErrorEvent) event);
        }
        if (event instanceof OutputCompleteEvent) {
            logger.debug("fireOutputCompleteEvent, " + event);
            eventsCallback.fireOutputCompleteEvent((OutputCompleteEvent) event);
        }
    }

    private void addEvent(JposEvent event) {
        eventQueue.offer(event);
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
                return "Весы ШТРИХ-М POS2";
            case Shtrih5:
                return "Весы ШТРИХ-М ШТРИХ5";
            case Shtrih6:
                return "Весы ШТРИХ-М ШТРИХ6";
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

        // Проверяем состояние ошибки
        if (state == S_ERROR) {
            return JPOS_S_ERROR;
        }

        // Проверяем, выполняется ли асинхронная операция
        if (asyncMode && asyncOperationInProgress) {
            result = JPOS_S_BUSY;
        } else {
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

        try {
            if (enabled) {
                state = S_ENABLED;
                readScaleWeight();
                setPowerState(JPOS_PS_ONLINE);

                // Запускаем pollThread только если pollEnabled == true
                if (pollEnabled) {
                    pollThread = new Thread(new PollTarget(this));
                    pollThread.setName("ScalePollThread");
                    pollThread.start();
                    logger.debug("Poll thread started");
                } else {
                    logger.debug("Poll thread is disabled by pollEnabled=false");
                }

                // Запускаем поток обработки событий
                if (eventThread == null) {
                    eventThread = new Thread(new EventTarget(this));
                    eventThread.setName("ScaleEventThread");
                    eventThread.start();
                }
            } else {
                state = S_CLAIMED;
                setPowerState(JPOS_PS_UNKNOWN);

                // Сбрасываем флаг операции
                asyncOperationInProgress = false;
                currentRequest = null;

                // Останавливаем асинхронный режим
                if (asyncMode) {
                    logger.debug("Stopping async mode");
                    setAsyncMode(false);
                }

                // Останавливаем pollThread
                if (pollThread != null) {
                    pollThread.interrupt();
                    try {
                        pollThread.join(1000);
                    } catch (InterruptedException e) {
                        logger.error("Error stopping pollThread", e);
                        Thread.currentThread().interrupt();
                    }
                    pollThread = null;
                    logger.debug("Poll thread stopped");
                }

                // Очищаем очереди
                requests.clear();
                logger.debug("Requests queue cleared");

                // Поток событий не останавливаем, он должен продолжать обрабатывать оставшиеся события
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

        // Переводим устройство в состояние ошибки
        state = S_ERROR;

        // Добавляем событие в очередь
        addEvent(event);

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
            logger.debug("checkClaimed = JPOS_E_NOTCLAIMED");
            throw new JposException(JPOS_E_NOTCLAIMED);
        }
    }

    private void checkEnabled() throws JposException {
        if (state < S_ENABLED) {
            logger.debug("checkEnabled() JPOS_E_DISABLED");
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
        } catch (InterruptedException e) {
            logger.debug("Poll thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Poll proc error", e);
        }
    }

    private ScaleWeight readScaleWeight() throws JposException {
        try {
            ScaleWeight weight = scale.getWeight();

            // Защита от NPE
            if (weight == null) {
                logger.warn("readScaleWeight: weight is null");
                return null;
            }

            if (weight.status == null) {
                logger.warn("readScaleWeight: weight.status is null");
                return null;
            }

            ScaleWeight previousWeight = m_weight;

            if (powerNotify == JPOS_PN_ENABLED) {
                setPowerState(JPOS_PS_ONLINE);
            }

            if (weight.status.isStable()) {
                scaleLiveWeight = weight.weight;
            }

            boolean isStableChanged = (previousWeight == null)
                    || (weight.status.isStable() != previousWeight.status.isStable());

            if (isStableChanged) {
                if (weight.status.isStable()) {
                    statusUpdateEvent(SCAL_SUE_STABLE_WEIGHT);
                } else {
                    statusUpdateEvent(SCAL_SUE_WEIGHT_UNSTABLE);
                }
            }

            boolean isZeroChanged = (weight.weight == 0)
                    && ((previousWeight == null) || (previousWeight.weight != 0));

            if (isZeroChanged) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_ZERO);
            }

            boolean isUnderZeroChanged = (weight.weight < 0)
                    && ((previousWeight == null) || (previousWeight.weight >= 0));

            if (isUnderZeroChanged) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_UNDER_ZERO);
            }

            boolean isOverweightChanged = weight.status.isOverweight()
                    && ((previousWeight == null) || (!previousWeight.status.isOverweight()));

            if (isOverweightChanged) {
                statusUpdateEvent(SCAL_SUE_WEIGHT_OVERWEIGHT);
            }

            m_weight = weight;
            return weight;

        } catch (Exception e) {
            logger.error("Exception in readScaleWeight", e);
            throw getJposException(e);
        }
    }

    public long readWeightTimeout(int timeout) throws JposException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            ScaleWeight weight = readScaleWeight();
            if (weight == null) {
                return 0;
            }

            if (weight.status.isStable()) {
                // Проверка на перегруз
                if (weight.status.isOverweight()) {
                    throw new JposException(JPOS_E_EXTENDED,
                            ScaleConst.JPOS_ESCAL_OVERWEIGHT,
                            "Вес превышает максимальный");
                }

                // Обработка нулевого веса
                if (weight.weight == 0) {
                    if (zeroValid) {
                        return weight.weight;
                    }
                    // Если zeroValid = false, продолжаем ждать стабильный ненулевой вес
                    // НЕ возвращаем 0
                } else {
                    return weight.weight;
                }
            }

            // Проверка таймаута
            if (timeout == 0) {
                return weight.weight; // Возвращаем текущий вес (возможно нестабильный)
            }

            if (System.currentTimeMillis() > (startTime + timeout)) {
                return weight.weight; // Возвращаем текущий вес по таймауту
            }
            Thread.sleep(10);
        }
        throw new InterruptedException("Thread interrupted while reading weight");
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
        logger.debug("Weight processing thread started");
        try {
            while (!Thread.interrupted()) {
                try {
                    // Берем запрос из очереди, блокируясь до появления элемента
                    currentRequest = requests.take();
                } catch (InterruptedException e) {
                    logger.debug("Weight thread interrupted while waiting for request");
                    Thread.currentThread().interrupt();
                    break;
                }

                // Обрабатываем запрос
                boolean retry = false;
                ErrorEvent errorEvent = null;

                do {
                    retry = false;
                    try {
                        logger.debug("Processing weight request with timeout: " + currentRequest.getTimeout());
                        long weight = readWeightTimeout(currentRequest.getTimeout());
                        DataEvent event = new DataEvent(this, (int) weight);
                        addEvent(event);
                        logger.debug("Weight request processed, weight: " + weight);
                    } catch (JposException e) {
                        logger.error("Error processing weight request", e);

                        // Создаем ErrorEvent
                        errorEvent = new ErrorEvent(this,
                                e.getErrorCode(), e.getErrorCodeExtended(),
                                JPOS_EL_INPUT, // Locus = INPUT
                                JPOS_ER_CLEAR); // Default response = CLEAR

                        // Переводим устройство в состояние ошибки
                        state = S_ERROR;

                        // Добавляем событие в очередь и синхронизируемся для wait/notify
                        synchronized (errorEvent) {
                            addEvent(errorEvent);

                            // Ждем, пока приложение обработает событие и изменит ErrorResponse
                            try {
                                logger.debug("Waiting for ErrorEvent processing...");
                                errorEvent.wait(5000); // Ждем до 5 секунд
                            } catch (InterruptedException ie) {
                                logger.debug("Wait interrupted");
                                Thread.currentThread().interrupt();
                            }
                        }

                        // Проверяем ответ приложения
                        if (errorEvent.getErrorResponse() == JPOS_ER_RETRY) {
                            logger.debug("Retrying operation after ER_RETRY");
                            retry = true;
                            // Сбрасываем состояние ошибки
                            state = S_ENABLED;
                        } else if (errorEvent.getErrorResponse() == JPOS_ER_CLEAR) {
                            logger.debug("Clearing after ER_CLEAR");
                            // Очищаем очередь
                            eventQueue.clear();
                            // Сбрасываем состояние ошибки
                            state = S_ENABLED;
                        }
                    } catch (InterruptedException e) {
                        logger.debug("Weight thread interrupted during processing");
                        Thread.currentThread().interrupt();
                        break;
                    }
                } while (retry);

                // Сбрасываем флаг операции после завершения (успех или ошибка)
                asyncOperationInProgress = false;
                currentRequest = null;

                // Уведомляем ErrorEvent, если он все еще ждет (на случай таймаута)
                if (errorEvent != null) {
                    synchronized (errorEvent) {
                        errorEvent.notifyAll();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error in weightProc", e);
        } finally {
            logger.debug("Weight processing thread stopped");
            asyncOperationInProgress = false;
            currentRequest = null;
        }
    }

    private JposException getJposException(Exception e) {
        logger.error("Exception caught", e);
        if (e instanceof JposException) {
            return (JposException) e;
        }
        if (e instanceof DeviceError) {
            DeviceError deviceError = (DeviceError) e;
            switch (deviceError.getCode()) {
                case IDevice.ERROR_NOLINK: {
                    setPowerState(JPOS_PS_OFF_OFFLINE);
                    // Используем более подходящий код ошибки
                    return new JposException(JPOS_E_OFFLINE, e.getMessage());
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

    // === Методы из версии 1.14 (заглушки, выбрасывающие исключения) ===
    public void doPriceCalculating(int[] weightData, int[] tare, long[] unitPrice,
            long[] unitPriceX, int[] weightUnitX,
            int[] weightNumeratorX, int[] weightDenominatorX,
            long[] price, int timeout) throws JposException {
        logger.debug("doPriceCalculating()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void freezeValue(int item, boolean freeze) throws JposException {
        logger.debug("freezeValue()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void readLiveWeightWithTare(int[] weightData, int[] tare, int timeout) throws JposException {
        logger.debug("readLiveWeightWithTare()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void setPriceCalculationMode(int mode) throws JposException {
        logger.debug("setPriceCalculationMode()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void setSpecialTare(int mode, int data) throws JposException {
        logger.debug("setSpecialTare()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void setTarePriority(int priority) throws JposException {
        logger.debug("setTarePriority()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

    public void setUnitPriceWithWeightUnit(long unitPrice, int weightUnit,
            int weightNumerator, int weightDenominator) throws JposException {
        logger.debug("setUnitPriceWithWeightUnit()");
        throw new JposException(JPOS_E_ILLEGAL, "Не поддерживается");
    }

}
