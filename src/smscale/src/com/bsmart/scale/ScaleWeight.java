package com.bsmart.scale;

public class ScaleWeight {

    public final long weight;
    public final long tare;
    public final ScaleStatus status;

    public ScaleWeight(long weight, long tare, ScaleStatus status) {
        this.weight = weight;
        this.tare = tare;
        this.status = status;
    }

    public long getWeight() {
        return weight;
    }

    public long getTare() {
        return tare;
    }

    public ScaleStatus getStatus() {
        return status;
    }

    public String toString() {
        String s;
        float fWeight, fTara;
        fWeight = (float) weight / 1000.0f;
        fTara = (float) tare / 1000.0f;
        s = "Вес (" + (status.isStable() ? "стабилен" : "не стабилен") + "): " + fWeight + " кг. \r\n";
        s += "Тара: " + fTara + " кг.";
        return s;
    }

}
