package com.saee.addecision.events;

import com.saee.addecision.repository.CampaignRepository;
import com.saee.addecision.service.BudgetTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies impression and click events to PostgreSQL and the Redis spend counter.
 * Kafka delivers at least once, so events are de-duplicated by event id.
 */
@Component
public class SpendConsumer {

    private static final Logger log = LoggerFactory.getLogger(SpendConsumer.class);

    private final CampaignRepository repository;
    private final BudgetTracker budgetTracker;

    public SpendConsumer(CampaignRepository repository, BudgetTracker budgetTracker) {
        this.repository = repository;
        this.budgetTracker = budgetTracker;
    }

    @KafkaListener(topics = "${ads.kafka.topic}", groupId = "spend-updater")
    @Transactional
    public void onEvent(AdEvent event) {
        if (!budgetTracker.firstTimeSeen(event.eventId())) {
            log.debug("Skipping duplicate event {}", event.eventId());
            return;
        }
        switch (event.type()) {
            case IMPRESSION -> repository.incrementImpressions(event.campaignId());
            case CLICK -> {
                repository.recordClick(event.campaignId(), event.costCents());
                budgetTracker.addSpend(event.campaignId(), event.costCents());
            }
        }
    }
}
