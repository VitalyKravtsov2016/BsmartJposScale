/**
 *
 * @author User
 */
package com.bsmart;

import java.util.prefs.Preferences;
import com.bsmart.IDevice;
import com.bsmart.scale.Pos2Serial;
import com.bsmart.scale.ScaleWeight;
import com.bsmart.scale.DeviceMetrics;
import com.bsmart.scale.ChannelParams;
import com.bsmart.scale.CalibrationStatus;
import com.bsmart.scale.SmScale;

public class ScaleCLI {

    private final Pos2Serial scale;

    public ScaleCLI() {
        this.scale = new Pos2Serial();
        loadSettings();
    }

    public Preferences getPreferences() {
        return Preferences.userNodeForPackage(SmScale.class);
    }

    public void loadSettings() {
        try {
            Preferences prefs = getPreferences();
            int portType = prefs.getInt(IDevice.PARAM_PORTTYPE, IDevice.PARAM_PORTTYPE_SERIAL);
            String portName = prefs.get(IDevice.PARAM_PORTNAME, "/dev/ttyACM0");
            int baudRate = prefs.getInt(IDevice.PARAM_BAUDRATE, 9600);
            int readTimeout = prefs.getInt(IDevice.PARAM_READ_TIMEOUT, 500);
            int openTimeout = prefs.getInt(IDevice.PARAM_OPEN_TIMEOUT, 1000);
            String password = prefs.get(IDevice.PARAM_PASSWORD, "0030");

            scale.setParam(IDevice.PARAM_PORTTYPE, String.valueOf(portType));
            scale.setParam(IDevice.PARAM_PORTNAME, portName);
            scale.setParam(IDevice.PARAM_BAUDRATE, String.valueOf(baudRate));
            scale.setParam(IDevice.PARAM_DATABITS, "8");
            scale.setParam(IDevice.PARAM_STOPBITS, "1");
            scale.setParam(IDevice.PARAM_PARITY, "0");
            scale.setParam(IDevice.PARAM_OPEN_TIMEOUT, String.valueOf(openTimeout));
            scale.setParam(IDevice.PARAM_READ_TIMEOUT, String.valueOf(readTimeout));
            scale.setParam(IDevice.PARAM_PASSWORD, password);
        } catch (Exception e) {
            System.err.println("Ошибка загрузки настроек: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        ScaleCLI cli = new ScaleCLI();

        try {
            String command = args[0];
            switch (command) {
                case "connect":
                    cli.connect();
                    break;
                case "disconnect":
                    cli.disconnect();
                    break;
                case "weight":
                    cli.readWeight();
                    break;
                case "zero":
                    cli.setZero();
                    break;
                case "tare":
                    cli.setTare();
                    break;
                case "device-info":
                    cli.readDeviceInfo();
                    break;
                case "channel-info":
                    cli.readChannelInfo();
                    break;
                case "change-password":
                    if (args.length < 2) {
                        System.err.println("Требуется пароль");
                        System.exit(1);
                    }
                    cli.changePassword(args[1]);
                    break;
                case "exchange-params":
                    if (args.length == 1) {
                        cli.readExchangeParams();
                    } else if (args.length == 3) {
                        cli.writeExchangeParams(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                    } else {
                        System.err.println("Неверное количество параметров");
                        System.exit(1);
                    }
                    break;
                case "select-channel":
                    if (args.length < 2) {
                        System.err.println("Требуется номер канала");
                        System.exit(1);
                    }
                    cli.selectChannel(Integer.parseInt(args[1]));
                    break;
                case "enable-channel":
                    if (args.length < 2) {
                        System.err.println("Требуется состояние (true/false)");
                        System.exit(1);
                    }
                    cli.enableChannel(Boolean.parseBoolean(args[1]));
                    break;
                case "start-calibration":
                    cli.startCalibration();
                    break;
                case "stop-calibration":
                    cli.stopCalibration();
                    break;
                case "calibration-status":
                    cli.readCalibrationStatus();
                    break;
                case "adc-value":
                    cli.readADCValue();
                    break;
                case "set-port":
                    if (args.length < 2) {
                        System.err.println("Требуется имя порта");
                        System.exit(1);
                    }
                    cli.setPort(args[1]);
                    cli.saveSettings();
                    break;
                case "set-baudrate":
                    if (args.length < 2) {
                        System.err.println("Требуется скорость");
                        System.exit(1);
                    }
                    cli.setBaudrate(Integer.parseInt(args[1]));
                    cli.saveSettings();
                    break;

                default:
                    System.err.println("Неизвестная команда: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        } finally {
            cli.disconnect();
        }
    }

    private static void printUsage() {
        System.out.println("Использование: ScaleCLI <команда> [параметры]");
        System.out.println("\nКоманды:");
        System.out.println("  connect                    - Подключиться к весам");
        System.out.println("  disconnect                 - Отключиться от весов");
        System.out.println("  weight                     - Прочитать вес");
        System.out.println("  zero                       - Установить ноль");
        System.out.println("  tare                       - Установить тару");
        System.out.println("  device-info                - Информация об устройстве");
        System.out.println("  channel-info               - Информация о каналах");
        System.out.println("  change-password <пароль>   - Сменить пароль (4 цифры)");
        System.out.println("  exchange-params            - Прочитать параметры обмена");
        System.out.println("  exchange-params <скорость> <таймаут> - Установить параметры обмена");
        System.out.println("  select-channel <канал>     - Выбрать канал");
        System.out.println("  enable-channel <true/false> - Включить/выключить канал");
        System.out.println("  start-calibration          - Начать градуировку");
        System.out.println("  stop-calibration           - Остановить градуировку");
        System.out.println("  calibration-status         - Прочитать статус градуировки");
        System.out.println("  adc-value                  - Прочитать АЦП");
        System.out.println("  set-port <порт>            - Установить порт");
        System.out.println("  set-baudrate <скорость>    - Установить скорость");
    }

    private void connect() throws Exception {
        scale.connect();
        System.out.println("CONNECTED");
    }

    private void disconnect() {
        try {
            scale.disconnect();
        } catch (Exception e) {
            // Игнорируем ошибки при отключении
        }
    }

    private void readWeight() throws Exception {
        connect();
        ScaleWeight weight = scale.getWeight();
        System.out.println("WEIGHT:" + weight.getWeight());
        System.out.println("TARE:" + weight.getTare());
        System.out.println("STATUS:" + weight.getStatus());
    }

    private void setZero() throws Exception {
        connect();
        scale.zero();
        System.out.println("ZERO_SET");
    }

    private void setTare() throws Exception {
        connect();
        scale.tara();
        System.out.println("TARE_SET");
    }

    private void readDeviceInfo() throws Exception {
        connect();
        scale.readDeviceMetrics();
        DeviceMetrics metrics = scale.getDeviceMetrics();

        System.out.println("TYPE:" + metrics.getType());
        System.out.println("SUBTYPE:" + metrics.getSubType());
        System.out.println("VERSION:" + metrics.getMajorVersion() + "." + metrics.getMinorVersion());
        System.out.println("MODEL:" + metrics.getModel());
        System.out.println("LANG:" + metrics.getLang());
        System.out.println("DESCRIPTION:" + metrics.getDescription());
    }

    private void readChannelInfo() throws Exception {
        connect();

        scale.readChannelCount();
        int channelCount = scale.getChannelCount();
        System.out.println("CHANNEL_COUNT:" + channelCount);

        scale.readChannelNumber();
        System.out.println("CURRENT_CHANNEL:" + scale.getChannelNumber());

        for (int i = 0; i < channelCount; i++) {
            scale.readChannelParams(i);
            ChannelParams params = scale.getChannelParams();
            System.out.println("CHANNEL_" + i + "_MAX_WEIGHT:" + params.maxWeigth);
            System.out.println("CHANNEL_" + i + "_MIN_WEIGHT:" + params.minWeigth);
            System.out.println("CHANNEL_" + i + "_MAX_TARE:" + params.maxTare);
            System.out.println("CHANNEL_" + i + "_POINT_COUNT:" + params.pointCount);
        }
    }

    private void changePassword(String newPassword) throws Exception {
        connect();

        if (newPassword.length() != 4 || !newPassword.matches("\\d{4}")) {
            throw new Exception("Пароль должен состоять из 4 цифр");
        }

        scale.writeAdminPassword(newPassword);
        System.out.println("PASSWORD_CHANGED");
    }

    private void readExchangeParams() throws Exception {
        connect();
        scale.readExchangeParams(0);
        System.out.println("BAUD_RATE:" + scale.getExchangeBaudRate());
        System.out.println("TIMEOUT:" + scale.getExchangeByteTimeout());
    }

    private void writeExchangeParams(int baudRate, int timeout) throws Exception {
        connect();
        scale.writeExchangeParams(0, baudRate, timeout);
        System.out.println("EXCHANGE_PARAMS_SET");
    }

    private void selectChannel(int channel) throws Exception {
        connect();
        scale.selectChannel(channel);
        System.out.println("CHANNEL_SELECTED:" + channel);
    }

    private void enableChannel(boolean enable) throws Exception {
        connect();
        scale.enableChannel(enable);
        System.out.println("CHANNEL_" + (enable ? "ENABLED" : "DISABLED"));
    }

    private void startCalibration() throws Exception {
        connect();
        scale.startCalibration();
        System.out.println("CALIBRATION_STARTED");
    }

    private void stopCalibration() throws Exception {
        connect();
        scale.stopCalibration();
        System.out.println("CALIBRATION_STOPPED");
    }

    private void readCalibrationStatus() throws Exception {
        connect();
        scale.readCalibrationStatus();
        CalibrationStatus status = scale.getCalibrationStatus();
        System.out.println("CALIBRATION_STATUS:" + status.getStatus());
        System.out.println("POINT_NUMBER:" + status.getPointNumber());
        System.out.println("WEIGHT:" + status.getWeight());
    }

    private void readADCValue() throws Exception {
        connect();
        scale.readADCValue();
        System.out.println("ADC_VALUE:" + scale.getADCValue());
    }

    public void setPort(String port) {
        scale.setParam(IDevice.PARAM_PORTNAME, port);
        System.out.println("PORT_SET:" + port);
    }

    public void setBaudrate(int baudrate) {
        scale.setParam(IDevice.PARAM_BAUDRATE, String.valueOf(baudrate));
        System.out.println("BAUDRATE_SET:" + baudrate);
    }

    public String getPort() {
        return scale.getParam(IDevice.PARAM_PORTNAME);
    }

    public String getScaleParam(String param) {
        return scale.getParam(param);
    }

    public int getBaudrate() {
        return Integer.valueOf(scale.getParam(IDevice.PARAM_BAUDRATE));
    }

    public void saveSettings() {
        try {
            Preferences prefs = getPreferences();
            prefs.put(IDevice.PARAM_PORTTYPE, scale.getParam(IDevice.PARAM_PORTTYPE));
            prefs.put(IDevice.PARAM_PORTNAME, scale.getParam(IDevice.PARAM_PORTNAME));
            prefs.putInt(IDevice.PARAM_BAUDRATE, Integer.parseInt(scale.getParam(IDevice.PARAM_BAUDRATE)));
            prefs.putInt(IDevice.PARAM_OPEN_TIMEOUT, Integer.parseInt(scale.getParam(IDevice.PARAM_OPEN_TIMEOUT)));
            prefs.putInt(IDevice.PARAM_READ_TIMEOUT, Integer.parseInt(scale.getParam(IDevice.PARAM_READ_TIMEOUT)));
            prefs.put(IDevice.PARAM_PASSWORD, scale.getParam(IDevice.PARAM_PASSWORD));
            prefs.flush();
            
            System.out.println("SETTINGS_SAVED");
        } catch (Exception e) {
            System.err.println("Ошибка сохранения настроек: " + e.getMessage());
        }
    }
}
