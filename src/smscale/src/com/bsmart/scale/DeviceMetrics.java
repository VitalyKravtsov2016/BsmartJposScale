package com.bsmart.scale;

public class DeviceMetrics {

    private int type;
    private int subType;
    private int majorVersion;
    private int minorVersion;
    private int model;
    private int lang;
    private String description;

    // Только пустой конструктор
    public DeviceMetrics() {
    }

    public String toString() {
        String s;
        s = String.format("%s\r\n", description);
        s += String.format("Тип: %d.%d, версия: %d.%d\r\n", type, subType, majorVersion, minorVersion);
        s += String.format("Модель: %d, язык: %d", model, lang);
        return s;
    }

    // Геттеры и сеттеры для всех полей
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getSubType() {
        return subType;
    }

    public void setSubType(int subType) {
        this.subType = subType;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(int majorVersion) {
        this.majorVersion = majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    public int getModel() {
        return model;
    }

    public void setModel(int model) {
        this.model = model;
    }

    public int getLang() {
        return lang;
    }

    public void setLang(int lang) {
        this.lang = lang;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}