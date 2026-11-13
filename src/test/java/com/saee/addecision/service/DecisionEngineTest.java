package com.saee.addecision.service;

import com.saee.addecision.api.DecisionRequest;
import com.saee.addecision.model.CampaignSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineTest {

    private static final DecisionRequest SPORTS_SEARCH =
            new DecisionRequest("sports", "app_store_search", Map.of());

    private final DecisionEngine engine = new DecisionEngine();

    private static CampaignSnapshot campaign(long id, long bidCents, double ctr, String segment,
                                             String placement, long budgetCents) {
        return new CampaignSnapshot(id, "adv-" + id, "ad-" + id, "https://cdn.example.com/" + id + ".png",
                bidCents, ctr, Set.of(segment), Set.of(placement), budgetCents);
    }

    @Test
    void picksHighestExpectedValueNotHighestBid() {
        CampaignSnapshot highBidLowCtr = campaign(1, 1_000, 0.01, "sports", "app_store_search", 100_000); // 10.0
        CampaignSnapshot bestValue = campaign(2, 300, 0.05, "sports", "app_store_search", 100_000);       // 15.0

        var winner = engine.decide(SPORTS_SEARCH, List.of(highBidLowCtr, bestValue), Map.of());

        assertThat(winner).map(CampaignSnapshot::id).contains(2L);
    }

    @Test
    void filtersOutWrongPlacementAndWrongSegment() {
        CampaignSnapshot wrongPlacement = campaign(1, 5_000, 0.5, "sports", "news_feed", 100_000);
        CampaignSnapshot wrongSegment = campaign(2, 5_000, 0.5, "gaming", "app_store_search", 100_000);
        CampaignSnapshot match = campaign(3, 100, 0.01, "sports", "app_store_search", 100_000);

        var winner = engine.decide(SPORTS_SEARCH, List.of(wrongPlacement, wrongSegment, match), Map.of());

        assertThat(winner).map(CampaignSnapshot::id).contains(3L);
    }

    @Test
    void wildcardSegmentMatchesAnyUser() {
        CampaignSnapshot everyone = campaign(1, 100, 0.02, CampaignSnapshot.ALL_SEGMENTS, "app_store_search", 100_000);

        var winner = engine.decide(new DecisionRequest("travel", "app_store_search", Map.of()),
                List.of(everyone), Map.of());

        assertThat(winner).isPresent();
    }

    @Test
    void skipsCampaignThatCannotAffordAnotherClick() {
        CampaignSnapshot nearlyBroke = campaign(1, 300, 0.10, "sports", "app_store_search", 1_000);
        CampaignSnapshot healthy = campaign(2, 100, 0.05, "sports", "app_store_search", 100_000);

        // Campaign 1 has spent 800 of 1,000; one more 300-cent click would exceed the budget.
        var winner = engine.decide(SPORTS_SEARCH, List.of(nearlyBroke, healthy), Map.of(1L, 800L));

        assertThat(winner).map(CampaignSnapshot::id).contains(2L);
    }

    @Test
    void breaksTiesByLowestCampaignId() {
        CampaignSnapshot later = campaign(7, 200, 0.05, "sports", "app_store_search", 100_000);
        CampaignSnapshot earlier = campaign(3, 200, 0.05, "sports", "app_store_search", 100_000);

        var winner = engine.decide(SPORTS_SEARCH, List.of(later, earlier), Map.of());

        assertThat(winner).map(CampaignSnapshot::id).contains(3L);
    }

    @Test
    void returnsEmptyWhenNothingIsEligible() {
        var winner = engine.decide(SPORTS_SEARCH, List.of(), Map.of());

        assertThat(winner).isEmpty();
    }
}
