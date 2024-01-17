package com.example.revDau.service;

import com.example.revDau.Utility.*;
import com.example.revDau.kafka.KafkaConstant;
import com.example.revDau.model.Policy;
import com.example.revDau.repository.PolicyRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class HealthMetricsService {
// sockjs-webSocket
    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthMetricsService.class);

    private final Map<String, List<JSONObject>> alertCache = new HashMap<>();
    private final Map<String, JSONObject> activeAlert = new HashMap<>();
    private final Map<String, Long> autoClearQueue = new HashMap<>();
   private final Map<Long, Values> expiringQueue = new HashMap<>();


    public static final int MINIMUM_INSPECTION_INTERVAL_SEC = 1;
    private static class Values {
        private final String identifier;
        private Long expiryTime;

        public Values(String identifier, Long expiryTime) {
            this.identifier = identifier;
            this.expiryTime = expiryTime;
        }

        public Values update(Long remaining) {
            this.expiryTime = remaining;
            return this;
        }
    }
    @EventListener
    public void handleHealthMetricsEvent(HealthMetricsEvent event) {

        try {
            HashMap<String,Object> metrics = (HashMap<String, Object>) event.getSource();
           // System.out.println("metrics = " + metrics);
            List<Policy> policies = policyRepository.findAll();
            if (!policies.isEmpty()) {
                for (Policy policy : policies) {
                    String serviceName = policy.getServiceName();

                    Object object =metrics.get("service.name");

                    if (object.toString().equals(serviceName)) {
                        String indicators = policy.getPolicyContext().getIndicators();
                      Object obj=  metrics.get(indicators);
                        if (!metrics.get(indicators).equals(indicators)) {
                            evaluate(policy, new BigDecimal(String.valueOf(obj)));
                        }
                    }
                }
                }

        } catch(Exception  e){
                LOGGER.error(e.getMessage());
        }
    }

    @Scheduled(fixedRate = MINIMUM_INSPECTION_INTERVAL_SEC * 1000L)
    public void inspect() {
        try {

            if (!expiringQueue.isEmpty()) {

                var iterator = expiringQueue.entrySet().iterator();

                while (iterator.hasNext()) {

                    var entry = iterator.next();

                    long remaining = entry.getValue().expiryTime - MINIMUM_INSPECTION_INTERVAL_SEC;

                    if (remaining <= 0) {

                        List<JSONObject> alerts = alertCache.get(entry.getValue().identifier);

                        if (alerts != null) {

                            alerts.removeIf(e -> Objects.equals(e.getLong(GlobalConstants.ID), entry.getKey()));

                            if (alerts.isEmpty()) {
                                alertCache.remove(entry.getValue().identifier);
                            }
                        }

                        iterator.remove();

                    } else {

                        entry.setValue(entry.getValue().update(remaining));
                    }
                }
            }

            if (!autoClearQueue.isEmpty()) {

                var iterator = autoClearQueue.entrySet().iterator();

                while (iterator.hasNext()) {

                    var entry = iterator.next();

                    long remaining = entry.getValue() - MINIMUM_INSPECTION_INTERVAL_SEC;

                    if (remaining <= 0) {

                        alertCache.remove(entry.getKey());

                        JSONObject alert = activeAlert.get(entry.getKey());
                        alert.put(GlobalConstants.STATUS, GlobalConstants.STATUS_CLEAR);
                        alert.put(GlobalConstants.TIMESTAMP, DatetimeUtil.now());


                        kafkaTemplate.send(KafkaConstant.ALERT, String.valueOf(alert));

                        activeAlert.remove(entry.getKey());

                        iterator.remove();


                    } else {
                        entry.setValue(remaining);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error(String.valueOf(e));
        }
    }

    public void evaluate(Policy policy, BigDecimal metricValue) {

        AlertConstants.Severity severity = AlertConstants.Severity.highest();


        while (severity != null) {
             Object threshold = policy.getThreshold();

             String s=severity.getName();
            if (Objects.equals(s, "critical")) {
                threshold = policy.getThreshold().getCritical();
            } else if (Objects.equals(s, "major")) {
                threshold = policy.getThreshold().getMajor();
            } else if (Objects.equals(s, "warning")) {
                threshold = policy.getThreshold().getWarning();
            }

            int comparator = metricValue.compareTo((BigDecimal) threshold);

            if (switch (AlertConstants.Operator.of(policy.getPolicyContext().getOperator())) {
                case GREATER_THAN -> comparator > 0;
                case GREATER_OR_EQUALS -> comparator >= 0;
                case LESS_THAN -> comparator < 0;
                case LESS_OR_EQUALS -> comparator <= 0;
                case EQUALS -> comparator == 0;
                case NOT_EQUALS -> comparator != 0;
            }) {

                String identifier = policy.getServiceName();

                alertCache.computeIfAbsent(identifier, e -> new ArrayList<>());
                JSONObject alert = new JSONObject().put(GlobalConstants.ID, CommonUtil.generateID())
                        .put(AlertConstants.SEVERITY, severity.getName())
                        .put("policy", policy.get_id())
                        .put(GlobalConstants.STATUS, GlobalConstants.STATUS_ACTIVE)
                        .put("indicator",policy.getPolicyContext().getIndicators())
                        .put(AlertConstants.TRIGGERED_VALUE, metricValue)
                        .put(GlobalConstants.SERVICE_NAME,policy.getServiceName())
                        .put(GlobalConstants.MESSAGE, String.format("%s! %s's %s crossed threshold %s ",
                                severity.getName(),
                                policy.getPolicyContext().getIndicators(),
                                metricValue,
                                threshold))
                        .put("timestamp", DatetimeUtil.now());

                alertCache.get(identifier).add(alert);
                System.out.println("alert = " + alert);
                System.out.println("************************");
                System.out.println("alertCache = " + alertCache);

                expiringQueue.put(alert.getLong("policy"), new Values(identifier, (long) policy.getAlertContext().getTimeFrameSec()));


                var ref = new AtomicReference<>(severity);

                while (ref.get() != null) {

                    if (alertCache.get(identifier).stream()
                            .filter(a -> AlertConstants.Severity.of(a.getString(AlertConstants.SEVERITY)).order() >= ref.get().order())
                            .count() >= policy.getAlertContext().getOccurrence()) {
                        JSONObject activeAlert = new JSONObject(alert.toString());

                        // Add or update key-value pairs in the activeAlert object
                        activeAlert.put(AlertConstants.SEVERITY, ref.get().getName())
                                .put(GlobalConstants.SERVICE_NAME,policy.getServiceName())
                                .put(GlobalConstants.MESSAGE, String.format("%s! %s [%s]  has %d occurrences in %d seconds",
                                        severity.getName(), policy.getServiceName(), policy.getPolicyContext().getIndicators(),
                                        policy.getAlertContext().getOccurrence(), policy.getAlertContext().getTimeFrameSec()));


                        this.activeAlert.put(identifier, activeAlert);
                        autoClearQueue.put(identifier, (long) policy.getAlertContext().getAutoClearSec());
                        System.out.println("activeAlert = " + activeAlert);
                        System.out.println("autoClearQueue = " + autoClearQueue);

                        break;
                    }
                    ref.set(ref.get().lower());

                }

                kafkaTemplate.send(KafkaConstant.ALERT, String.valueOf(alert));
            break;

            }
            severity = severity.lower();
        }
    }
}


