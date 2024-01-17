package com.example.revDau.model;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "policies")
public class Policy {

    private Map<String, Object> data;

    public Policy() {
        this.data = new HashMap<>();
    }
    private long _id;
    private String policyName;
    private List<String> tags;
    private String serviceName;
    private PolicyContext policyContext;
    private Threshold threshold;
    private AlertContext alertContext;
    private NotificationContext notificationContext;
}

