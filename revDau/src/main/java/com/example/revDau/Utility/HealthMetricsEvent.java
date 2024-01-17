package com.example.revDau.Utility;

import org.springframework.context.ApplicationEvent;

import java.util.HashMap;

public class HealthMetricsEvent extends ApplicationEvent {

    public HealthMetricsEvent(HashMap<String, Object> source) {
        super(source);
    }
}