package com.example.revDau.model;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Document(collection = "metrics")
public class MetricsData {

  //  private String id;
    private long pid;
    private String serviceName;
    private String user;
    private BigDecimal cpuPercentage;
    private BigDecimal memoryUsedPercentage;
    private long virtualMemoryBytes;
    private long memoryUsedBytes;
    private long threads;
    private long uptimeSec;
    private String startTime;
    private String processCommand;
    private long timestamp;

}
