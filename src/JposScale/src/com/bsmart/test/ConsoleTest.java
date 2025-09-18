package com.bsmart.test;

import java.util.Scanner;
import java.util.prefs.Preferences;
import com.bsmart.IDevice;
import com.bsmart.scale.Pos2Serial;
import com.bsmart.scale.ScaleWeight;
import com.bsmart.scale.DeviceMetrics;
import com.bsmart.scale.ChannelParams;
import com.bsmart.scale.CalibrationStatus;
import com.bsmart.scale.SmScale;

public class ConsoleTest {
    private final Pos2Serial scale;
    private final Scanner scanner;
    private boolean connected = false;

    public ConsoleTest() {
        this.scale = new Pos2Serial();
        this.scanner = new Scanner(System.in, "Cp1251");
        loadSettings();
    }
    
    public static void main(String[] args) {
        ConsoleTest app = new ConsoleTest();
        app.run();
    }

    private void loadSettings() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(SmScale.class);
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

            System.out.println("Настройки загружены:");
            System.out.println("Тип порта: " + portType);
            System.out.println("Имя порта: " + portName);
            System.out.println("Скорость: " + baudRate);
            System.out.println("Таймаут чтения: " + readTimeout + " мс");
            System.out.println("Таймаут открытия: " + openTimeout + " мс");
            System.out.println("Пароль: " + password);
        } catch (Exception e) {
            System.out.println("Ошибка загрузки настроек: " + e.getMessage());
        }
    }

    private void saveSettings() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ConsoleTest.class);
            prefs.put(IDevice.PARAM_PORTTYPE, scale.getParam(IDevice.PARAM_PORTTYPE));
            prefs.put(IDevice.PARAM_PORTNAME, scale.getParam(IDevice.PARAM_PORTNAME));
            prefs.putInt(IDevice.PARAM_BAUDRATE, Integer.parseInt(scale.getParam(IDevice.PARAM_BAUDRATE)));
            prefs.putInt(IDevice.PARAM_OPEN_TIMEOUT, Integer.parseInt(scale.getParam(IDevice.PARAM_OPEN_TIMEOUT)));
            prefs.putInt(IDevice.PARAM_READ_TIMEOUT, Integer.parseInt(scale.getParam(IDevice.PARAM_READ_TIMEOUT)));
            prefs.put(IDevice.PARAM_PASSWORD, scale.getParam(IDevice.PARAM_PASSWORD));
            System.out.println("Настройки сохранены");
        } catch (Exception e) {
            System.out.println("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    public void run() {
        System.out.println("=== КОНСОЛЬНОЕ ПРИЛОЖЕНИЕ ДЛЯ РАБОТЫ С ВЕСАМИ ===");
        
        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();
            
            try {
                switch (choice) {
                    case "1": connect(); break;
                    case "2": disconnect(); break;
                    case "3": readWeight(); break;
                    case "4": setZero(); break;
                    case "5": setTare(); break;
                    case "6": readDeviceInfo(); break;
                    case "7": readChannelInfo(); break;
                    case "8": changePassword(); break;
                    case "9": exchangeParamsMenu(); break;
                    case "10": channelManagementMenu(); break;
                    case "11": calibrationMenu(); break;
                    case "12": settingsMenu(); break;
                    case "0": 
                        disconnect();
                        System.out.println("Выход из программы");
                        return;
                    default:
                        System.out.println("Неверный выбор. Попробуйте снова.");
                }
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
            
            System.out.println("\nНажмите Enter для продолжения...");
            scanner.nextLine();
        }
    }

    private void printMenu() {
        System.out.println("\n=== ГЛАВНОЕ МЕНЮ ===");
        System.out.println("1. Подключиться к весам");
        System.out.println("2. Отключиться от весов");
        System.out.println("3. Прочитать вес");
        System.out.println("4. Установить ноль");
        System.out.println("5. Установить тару");
        System.out.println("6. Информация об устройстве");
        System.out.println("7. Информация о каналах");
        System.out.println("8. Сменить пароль");
        System.out.println("9. Параметры обмена");
        System.out.println("10. Управление каналами");
        System.out.println("11. Градуировка");
        System.out.println("12. Настройки");
        System.out.println("0. Выход");
        System.out.print("Выберите действие: ");
    }

    private void connect() {
        try {
            scale.connect();
            connected = true;
            System.out.println("Успешно подключено к весам");
        } catch (Exception e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
            connected = false;
        }
    }

    private void disconnect() {
        try {
            if (connected) {
                scale.disconnect();
                connected = false;
                System.out.println("Отключено от весов");
            }
        } catch (Exception e) {
            System.out.println("Ошибка отключения: " + e.getMessage());
        }
    }

    private void readWeight() throws Exception {
        checkConnection();
        ScaleWeight weight = scale.getWeight();
        System.out.println("Вес: " + weight.getWeight());
        System.out.println("Тара: " + weight.getTare());
        System.out.println("Статус: " + weight.getStatus());
    }

    private void setZero() throws Exception {
        checkConnection();
        scale.zero();
        System.out.println("Ноль установлен");
    }

    private void setTare() throws Exception {
        checkConnection();
        scale.tara();
        System.out.println("Тара установлена");
    }

    private void readDeviceInfo() throws Exception {
        checkConnection();
        scale.readDeviceMetrics();
        DeviceMetrics metrics = scale.getDeviceMetrics();
        
        System.out.println("=== ИНФОРМАЦИЯ ОБ УСТРОЙСТВЕ ===");
        System.out.println("Тип: " + metrics.getType());
        System.out.println("Подтип: " + metrics.getSubType());
        System.out.println("Версия: " + metrics.getMajorVersion() + "." + metrics.getMinorVersion());
        System.out.println("Модель: " + metrics.getModel());
        System.out.println("Язык: " + metrics.getLang());
        System.out.println("Описание: " + metrics.getDescription());
    }

    private void readChannelInfo() throws Exception {
        checkConnection();
        
        scale.readChannelCount();
        System.out.println("Количество каналов: " + scale.getChannelCount());
        
        scale.readChannelNumber();
        System.out.println("Текущий канал: " + scale.getChannelNumber());
        
        for (int i = 0; i < scale.getChannelCount(); i++) {
            scale.readChannelParams(i);
            ChannelParams params = scale.getChannelParams();
            System.out.println("\n=== КАНАЛ " + i + " ===");
            System.out.println("Макс. вес: " + params.maxWeigth);
            System.out.println("Мин. вес: " + params.minWeigth);
            System.out.println("Макс. тара: " + params.maxTare);
            System.out.println("Точек градуировки: " + params.pointCount);
        }
    }

    private void changePassword() throws Exception {
        checkConnection();
        System.out.print("Введите новый пароль (4 цифры): ");
        String newPassword = scanner.nextLine().trim();
        
        if (newPassword.length() != 4 || !newPassword.matches("\\d{4}")) {
            throw new Exception("Пароль должен состоять из 4 цифр");
        }
        
        scale.writeAdminPassword(newPassword);
        System.out.println("Пароль успешно изменен");
    }

    private void exchangeParamsMenu() throws Exception {
        checkConnection();
        
        System.out.println("\n=== ПАРАМЕТРЫ ОБМЕНА ===");
        System.out.println("1. Прочитать параметры");
        System.out.println("2. Установить параметры");
        System.out.print("Выберите действие: ");
        
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                scale.readExchangeParams(0);
                System.out.println("Скорость: " + scale.getExchangeBaudRate());
                System.out.println("Таймаут: " + scale.getExchangeByteTimeout() + "мс");
                break;
            case "2":
                System.out.print("Скорость (0-2400, 1-4800, 2-9600, 3-19200, 4-38400, 5-57600, 6-115200): ");
                int baudRate = Integer.parseInt(scanner.nextLine());
                System.out.print("Таймаут (мс): ");
                int timeout = Integer.parseInt(scanner.nextLine());
                
                scale.writeExchangeParams(0, baudRate, timeout);
                System.out.println("Параметры установлены");
                break;
            default:
                System.out.println("Неверный выбор");
        }
    }

    private void channelManagementMenu() throws Exception {
        checkConnection();
        
        System.out.println("\n=== УПРАВЛЕНИЕ КАНАЛАМИ ===");
        System.out.println("1. Выбрать канал");
        System.out.println("2. Включить/выключить канал");
        System.out.print("Выберите действие: ");
        
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                System.out.print("Номер канала: ");
                int channel = Integer.parseInt(scanner.nextLine());
                scale.selectChannel(channel);
                System.out.println("Канал " + channel + " выбран");
                break;
            case "2":
                System.out.print("Включить? (1-да, 0-нет): ");
                int enable = Integer.parseInt(scanner.nextLine());
                scale.enableChannel(enable == 1);
                System.out.println("Канал " + (enable == 1 ? "включен" : "выключен"));
                break;
            default:
                System.out.println("Неверный выбор");
        }
    }

    private void calibrationMenu() throws Exception {
        checkConnection();
        
        System.out.println("\n=== ГРАДУИРОВКА ===");
        System.out.println("1. Начать градуировку");
        System.out.println("2. Остановить градуировку");
        System.out.println("3. Прочитать статус градуировки");
        System.out.println("4. Прочитать АЦП");
        System.out.print("Выберите действие: ");
        
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                scale.startCalibration();
                System.out.println("Градуировка начата");
                break;
            case "2":
                scale.stopCalibration();
                System.out.println("Градуировка остановлена");
                break;
            case "3":
                scale.readCalibrationStatus();
                CalibrationStatus status = scale.getCalibrationStatus();
                System.out.println("Статус градуировки: " + status.getStatus());
                System.out.println("Точка: " + status.getPointNumber());
                System.out.println("Вес: " + status.getWeight());
                break;
            case "4":
                scale.readADCValue();
                System.out.println("Показания АЦП: " + scale.getADCValue());
                break;
            default:
                System.out.println("Неверный выбор");
        }
    }

    private void settingsMenu() {
        System.out.println("\n=== НАСТРОЙКИ ===");
        System.out.println("1. Изменить порт");
        System.out.println("2. Изменить скорость");
        System.out.println("3. Изменить таймаут");
        System.out.println("4. Сохранить настройки");
        System.out.print("Выберите действие: ");
        
        String choice = scanner.nextLine().trim();
        try {
            switch (choice) {
                case "1":
                    System.out.print("Имя порта (COM1, COM2, ...): ");
                    String port = scanner.nextLine();
                    scale.setParam(IDevice.PARAM_PORTNAME, port);
                    System.out.println("Порт изменен на: " + port);
                    break;
                case "2":
                    System.out.print("Скорость (2400, 4800, 9600, ...): ");
                    int baud = Integer.parseInt(scanner.nextLine());
                    scale.setParam(IDevice.PARAM_BAUDRATE, String.valueOf(baud));
                    System.out.println("Скорость изменена на: " + baud);
                    break;
                case "3":
                    System.out.print("Таймаут (мс): ");
                    int timeout = Integer.parseInt(scanner.nextLine());
                    scale.setParam(IDevice.PARAM_OPEN_TIMEOUT, String.valueOf(timeout));
                    System.out.println("Таймаут изменен на: " + timeout + "мс");
                    break;
                case "4":
                    saveSettings();
                    break;
                default:
                    System.out.println("Неверный выбор");
            }
        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private void checkConnection() throws Exception {
        if (!connected) {
            throw new Exception("Не подключено к весам. Сначала выполните подключение.");
        }
    }
}