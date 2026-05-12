package com.taidev.warehouse.common;

public final class AppValidators {

    private AppValidators() {}

    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void requirePositive(int value, String message) {
        if (value <= AppNumbers.ZERO) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void requireNonNegative(int value, String message) {
        if (value < AppNumbers.ZERO) {
            throw new IllegalArgumentException(message);
        }
    }
}
