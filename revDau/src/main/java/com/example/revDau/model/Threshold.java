package com.example.revDau.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Threshold {
    private BigDecimal critical;
    private BigDecimal major;
    private BigDecimal warning;
}
