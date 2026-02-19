package com.bsmart.util;

public class ServiceVersionUtil {

    public static int getVersionInt() {
        int result;
        String parts[] = ServiceVersion.VERSION.split("\\.");
        try {
            if (parts.length < 2) {
                return 0;
            }

            result = Integer.parseInt(parts[0]) * 100 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            result = 0;
        }
        return result;
    }
}
