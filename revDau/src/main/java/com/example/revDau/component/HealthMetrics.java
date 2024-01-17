package com.example.revDau.component;

import com.example.revDau.Utility.DatetimeUtil;
import com.example.revDau.Utility.HealthMetricsEvent;
import com.example.revDau.kafka.KafkaConstant;
import com.example.revDau.repository.HealthMetricsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Component
@Service
public class HealthMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthMetrics.class);

    String vmName=null;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private HealthMetricsRepository healthMetricsRepository;

    HashMap<String,Object>metrics=new HashMap<>();
  @Autowired
    private KafkaTemplate<Object, String> kafkaTemplate;

  @Scheduled(fixedRate = 60000)
  public void collectMetrics() {
          vmName();
          collectApplicationMetrics(HealthMetricConstants.NMS_SERVICE);
        collectApplicationMetrics(HealthMetricConstants.KAFKA_SERVICE);
        collectApplicationMetrics(HealthMetricConstants.MONGO_SERVICE);
        collectApplicationMetrics(HealthMetricConstants.DRUID_SERVICE);
        collectNFACCTDMetrics();
    }

    private void vmName() {
        String command=HealthMetricConstants.HOST_NAME;

        executeCommand(command)
                .thenCompose(handler -> {
                    if (handler != null && !handler.isEmpty()) {
                        vmName=handler;


                    } else {
                        LOGGER.warn("Error getting Host name");
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }



    public void collectNFACCTDMetrics() {
        String nfacctdCommand = String.format("ps aux | grep \"%s: Core Process \\[default\\]\"", HealthMetricConstants.NFACCTD_SERVICE);
        executeCommand(nfacctdCommand)
                .thenCompose(pid -> {
                    String nfacctPid = null;
                    if (pid != null) {

                        String[] nfacctData = pid.split("\\s+");
                        nfacctPid = nfacctData[1];
                    }
                    return collectMetricsForProcess(nfacctPid, HealthMetricConstants.NFACCTD_SERVICE);
                })
                .join();
    }

    public void collectApplicationMetrics(String serviceName) {
        String statusCommand = "systemctl status " + serviceName + " | grep PID";
        executeCommand(statusCommand)
                .thenCompose(pid -> {
                    String processPid = null;
                    if (pid != null) {
                        String[] processData = pid.split("\\s+");
                        processPid = processData[2];
                    }
                    return collectMetricsForProcess(processPid, serviceName);
                }).join();
    }

    private CompletableFuture<Void> collectMetricsForProcess(String processPid, String serviceName) {
        String command=String.format("ps -p %s -o pid,user,%%cpu,%%mem,vsz,rss,nlwp,etime,lstart,cmd --no-headers | awk '{printf \"%%s %%s %%.4f %%.4f %%s %%s %%s %%s %%s \", $1, $2, $3, $4, $5, $6, $7, $8, $9; for(i=10;i<=NF;++i) printf \"%%s \", $i; printf \"\\n\"}'\n",processPid);

        return executeCommand(command)
                .thenCompose(handler -> {
                    if (handler != null && !handler.isEmpty()) {

                        String[] serviceData = handler.split("\\s+");
                        metrics.put("host.name",vmName);
                        metrics.put("ip","");
                        metrics.put("pid", serviceData[0]);
                        metrics.put("service.name", serviceName);
                        metrics.put("user", serviceData[1]);
                        metrics.put("cpu.percentage",new BigDecimal(serviceData[2]));
                        metrics.put("memory.used.percentage", new BigDecimal(serviceData[3]));
                        metrics.put("virtual.memory.bytes", Long.parseLong(serviceData[4]) * 1024);
                        metrics.put("memory.used.bytes", Long.parseLong(serviceData[5]) * 1024);
                        metrics.put("threads", Long.parseLong(serviceData[6]));

                        String[] timeParts = serviceData[7].split("[-:]");
                        int timePartsSize = timeParts.length;
                        int day,hours, minutes, seconds;
                        switch (timePartsSize) {
                            case 4:
                                day = Integer.parseInt(timeParts[0]);
                                hours = Integer.parseInt(timeParts[1]);
                                minutes = Integer.parseInt(timeParts[2]);
                                seconds = Integer.parseInt(timeParts[3]);
                                metrics.put("uptime.sec",(day*24*60*60)+(hours * 3600) + (minutes * 60) + seconds);
                                break;
                            case 3:
                                hours = Integer.parseInt(timeParts[0]);
                                minutes = Integer.parseInt(timeParts[1]);
                                seconds = Integer.parseInt(timeParts[2]);
                                metrics.put("uptime.sec", (hours * 3600) + (minutes * 60) + seconds);
                                break;
                            case 2:
                                minutes = Integer.parseInt(timeParts[0]);
                                seconds = Integer.parseInt(timeParts[1]);
                                metrics.put("uptime.sec", (minutes * 60) + seconds);
                                break;
                            case 1:
                                seconds = Integer.parseInt(timeParts[0]);
                                metrics.put("uptime.sec", seconds);
                                break;
                        }


                        metrics.put("start.time", serviceData[8] + " " + serviceData[9] + " " + serviceData[10] + " " + serviceData[11] + " " + serviceData[12]);
                        String processCommand = String.join(" ", Arrays.copyOfRange(serviceData, 13, serviceData.length));
                        metrics.put("process.command", processCommand);
                        metrics.put("timestamp", DatetimeUtil.now());
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            String jsonString = objectMapper.writeValueAsString(metrics);

                            kafkaTemplate.send(KafkaConstant.HEALTH_TOPIC, jsonString);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }

                        HealthMetricsEvent healthMetricsEvent = new HealthMetricsEvent(metrics);
                        eventPublisher.publishEvent(healthMetricsEvent);
                        metrics.clear();
                    } else {
                        LOGGER.warn("Metrics for {} cannot be collected because the service is not running.", serviceName);
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    public CompletableFuture<String> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {


            try {
                String line ;
                Process process = new ProcessBuilder("/bin/bash", "-c", command).start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                if ((line = reader.readLine()) != null) {
                    return line.trim();
                }
            } catch (IOException e) {
                LOGGER.error("Error executing command: {}", e.getMessage());
            }
            return null;
        });
    }
}
