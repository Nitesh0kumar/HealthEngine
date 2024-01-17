package com.example.revDau;

import com.example.revDau.component.HealthMetrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RevDauApplication {

	public static void main(String[] args) {
		SpringApplication.run(RevDauApplication.class, args);
//
//		HealthMetrics healthMetrics= new HealthMetrics();
//		healthMetrics.collectMetrics();
	}
}
