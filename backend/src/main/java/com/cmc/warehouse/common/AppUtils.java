package com.cmc.warehouse.common;

public final class AppUtils {

    private AppUtils() {}

    public static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
