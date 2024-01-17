package com.example.revDau.model;

import lombok.Data;

@Data
public class AlertContext {
    private int occurrence;
    private int timeFrameSec;
    private int autoClearSec;

}
