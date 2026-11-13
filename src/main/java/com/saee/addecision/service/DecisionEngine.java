package com.saee.addecision.service;

import com.saee.addecision.api.DecisionRequest;
import com.saee.addecision.model.CampaignSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure decision logic with no I/O, so it is fast and easy to unit test.
 * 1. Filter by targeting (placement + segment).
 * 2. Drop campaigns that cannot afford another click.
 * 3. Rank by expected value (bid x predicted CTR); ties go to the lowest campaign id.
 */
@Component
public class DecisionEngine {

    public List<CampaignSnapshot> filterEligible(DecisionRequest request, List<CampaignSnapshot> campaigns) {
        List<CampaignSnapshot> eligible = new ArrayList<>();
        for (CampaignSnapshot c : campaigns) {
            if (c.matchesPlacement(request.placement()) && c.matchesSegment(request.userSegment())) {
                eligible.add(c);
            }
        }
        return eligible;
    }

    public Optional<CampaignSnapshot> pickBest(List<CampaignSnapshot> eligible, Map<Long, Long> spentByCampaign) {
        CampaignSnapshot best = null;
        for (CampaignSnapshot c : eligible) {
            long spent = spentByCampaign.getOrDefault(c.id(), 0L);
            if (!c.canAfford(spent)) {
                continue;
            }
            if (best == null || isBetter(c, best)) {
                best = c;
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<CampaignSnapshot> decide(DecisionRequest request, List<CampaignSnapshot> campaigns,
                                             Map<Long, Long> spentByCampaign) {
        return pickBest(filterEligible(request, campaigns), spentByCampaign);
    }

    private static boolean isBetter(CampaignSnapshot candidate, CampaignSnapshot current) {
        int cmp = Double.compare(candidate.expectedValue(), current.expectedValue());
        return cmp > 0 || (cmp == 0 && candidate.id() < current.id());
    }
}
