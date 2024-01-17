package com.example.revDau.repository;

import com.example.revDau.model.MetricsData;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HealthMetricsRepository extends MongoRepository<MetricsData, String> {
}
