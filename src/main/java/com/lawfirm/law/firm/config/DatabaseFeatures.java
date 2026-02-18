package com.lawfirm.law.firm.config;

public final class DatabaseFeatures {
    private static volatile boolean unaccentAvailable = false;

    private DatabaseFeatures() {}

    public static boolean isUnaccentAvailable() {
        return unaccentAvailable;
    }

    public static void setUnaccentAvailable(boolean available) {
        unaccentAvailable = available;
    }
}
