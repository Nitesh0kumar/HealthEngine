package com.example.revDau.Utility;

import java.util.concurrent.atomic.AtomicLong;

public class CommonUtil {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.nanoTime());

    public static long generateID() {
        return ID_GENERATOR.getAndIncrement();
    }
}
