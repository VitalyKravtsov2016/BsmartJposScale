package com.bsmart.jpos;

import jpos.config.JposEntry;
import jpos.config.simple.SimpleEntry;
import jpos.config.simple.SimpleRegPopulator
        SimpleProp;

import java.util.ArrayList;
import java.util.List;

/**
 * Строитель для создания JposEntry с параметрами.
 * Упрощает создание конфигурации для тестов.
 */
public class JposEntryBuilder {
    
    private final String logicalName;
    private final List<SimpleProp> properties = new ArrayList<>();
    
    private String vendorName = "SHTRIH-M";
    private String vendorUrl = "http://www.shtrih-m.ru";
    private String productName = "Bsmart Scale Service";
    private String productUrl = "http://www.bsmart.ru";
    private String productDescription = "Scale Service";
    private String jposCategory = "Scale";
    private String jposVersion = "1.13";
    private String factoryClass = "com.bsmart.jpos.scale.ScaleService";
    private String serviceClass = "com.bsmart.jpos.scale.ScaleService";
    
    /**
     * Конструктор с обязательным logicalName
     * @param logicalName логическое имя устройства
     */
    public JposEntryBuilder(String logicalName) {
        this.logicalName = logicalName;
    }
    
    /**
     * Добавить строковое свойство
     */
    public JposEntryBuilder addProperty(String name, String value) {
        properties.add(new SimpleProp(name, value));
        return this;
    }
    
    /**
     * Добавить целочисленное свойство
     */
    public JposEntryBuilder addProperty(String name, int value) {
        properties.add(new SimpleProp(name, value));
        return this;
    }
    
    /**
     * Добавить булево свойство
     */
    public JposEntryBuilder addProperty(String name, boolean value) {
        properties.add(new SimpleProp(name, value));
        return this;
    }
    
    /**
     * Установить информацию о производителе
     */
    public JposEntryBuilder withVendor(String name, String url) {
        this.vendorName = name;
        this.vendorUrl = url;
        return this;
    }
    
    /**
     * Установить информацию о продукте
     */
    public JposEntryBuilder withProduct(String name, String url, String description) {
        this.productName = name;
        this.productUrl = url;
        this.productDescription = description;
        return this;
    }
    
    /**
     * Установить информацию о JavaPOS
     */
    public JposEntryBuilder withJposInfo(String category, String version) {
        this.jposCategory = category;
        this.jposVersion = version;
        return this;
    }
    
    /**
     * Установить классы фабрики и сервиса
     */
    public JposEntryBuilder withServiceClasses(String factoryClass, String serviceClass) {
        this.factoryClass = factoryClass;
        this.serviceClass = serviceClass;
        return this;
    }
    
    /**
     * Создать JposEntry
     */
    public JposEntry build() {
        SimpleEntry entry = new SimpleEntry();
        
        // Устанавливаем логическое имя
        entry.setLogicalName(logicalName);
        
        // Устанавливаем информацию о производителе
        entry.setVendorName(vendorName);
        entry.setVendorURL(vendorUrl);
        
        // Устанавливаем информацию о продукте
        entry.setProductName(productName);
        entry.setProductURL(productUrl);
        entry.setProductDescription(productDescription);
        
        // Устанавливаем информацию о JavaPOS
        entry.setJposCategory(jposCategory);
        entry.setJposVersion(jposVersion);
        
        // Устанавливаем классы
        entry.setFactoryClass(factoryClass);
        entry.setServiceClass(serviceClass);
        
        // Добавляем все свойства
        for (SimpleProp prop : properties) {
            entry.addProperty(prop);
        }
        
        return entry;
    }
    
    /**
     * Создать JposEntry с стандартными параметрами для Scale
     */
    public static JposEntry createScaleEntry(String logicalName, String portName) {
        return new JposEntryBuilder(logicalName)
                .addProperty("portName", portName)
                .addProperty("protocol", "pos2")
                .addProperty("baudRate", "9600")
                .addProperty("timeout", "100")
                .addProperty("password", "30")
                .build();
    }
}