package com.saee.addecision.service;

import com.saee.addecision.api.ClickRequest;
import com.saee.addecision.api.DecisionRequest;
import com.saee.addecision.api.DecisionResponse;
import com.saee.addecision.events.AdEvent;
import com.saee.addecision.events.EventPublisher;
import com.saee.addecision.model.CampaignSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hot path per request: in-memory targeting filter, one Redis MGET for spend, rank,
 * then an async Kafka publish. No database calls.
 */
@Service
public class DecisionService {

    private final DecisionEngine engine;
    private final CampaignCache cache;
    private final BudgetTracker budgetTracker;
    private final EventPublisher publisher;

    public DecisionService(DecisionEngine engine, CampaignCache cache, BudgetTracker budgetTracker,
                           EventPublisher publisher) {
        this.engine = engine;
        this.cache = cache;
        this.budgetTracker = budgetTracker;
        this.publisher = publisher;
    }

    public Optional<DecisionResponse> decide(DecisionRequest request) {
        List<CampaignSnapshot> eligible = engine.filterEligible(request, cache.active());
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Long> spent = budgetTracker.spentFor(eligible.stream().map(CampaignSnapshot::id).toList());
        return engine.pickBest(eligible, spent).map(winner -> {
            String requestId = UUID.randomUUID().toString();
            publisher.publish(AdEvent.impression(requestId, winner));
            return new DecisionResponse(requestId, winner.id(), winner.adId(), winner.creativeUrl(),
                    winner.expectedValue());
        });
    }

    public boolean recordClick(ClickRequest click) {
        return cache.find(click.campaignId())
                .map(campaign -> {
                    publisher.publish(AdEvent.click(click.requestId(), campaign));
                    return true;
                })
                .orElse(false);
    }
}
