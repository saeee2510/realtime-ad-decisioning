package com.saee.addecision.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, AdEvent> kafka;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, AdEvent> kafka, @Value("${ads.kafka.topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    /** Asynchronous send, keyed by campaign id so each campaign's events stay in order. */
    public void publish(AdEvent event) {
        kafka.send(topic, Long.toString(event.campaignId()), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("Failed to publish {} for campaign {}", event.type(), event.campaignId(), error);
                    }
                });
    }
}
