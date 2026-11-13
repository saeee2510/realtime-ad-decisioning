package com.saee.addecision.model;

import java.util.Set;

/**
 * Immutable, cache-friendly view of a campaign used on the decision hot path.
 */
public record CampaignSnapshot(
        Long id,
        String advertiserId,
        String adId,
        String creativeUrl,
        long bidCents,
        double predictedCtr,
        Set<String> segments,
        Set<String> placements,
        long totalBudgetCents) {

    public static final String ALL_SEGMENTS = "*";

    public boolean matchesSegment(String segment) {
        return segments.contains(ALL_SEGMENTS) || segments.contains(segment);
    }

    public boolean matchesPlacement(String placement) {
        return placements.contains(placement);
    }

    /** Expected value of showing this ad once: bid x predicted CTR (cents per impression). */
    public double expectedValue() {
        return bidCents * predictedCtr;
    }

    /** True if the campaign can still pay for one more click. */
    public boolean canAfford(long spentCents) {
        return spentCents + bidCents <= totalBudgetCents;
    }
}
