/*
 * Copyright (c) 2023 RevDau Industries (P) Limited
 */

package com.example.revDau.Utility;

public class DatetimeUtil {

    /**
     * @return current time in seconds
     */
    public static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * @return current time in millis
     */
    public static long millis() {
        return System.currentTimeMillis();
    }

    /**
     * @return current time in nano
     */
    public static long nano() {
        return System.nanoTime();
    }
}
