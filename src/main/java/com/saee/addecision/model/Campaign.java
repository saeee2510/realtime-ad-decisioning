package com.saee.addecision.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A campaign as stored in PostgreSQL (the source of truth).
 * The hot path never reads this entity; it reads {@link CampaignSnapshot} from the cache.
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String advertiserId;

    @Column(nullable = false)
    private String adId;

    private String creativeUrl;

    /** Cost-per-click bid, in cents. */
    private long bidCents;

    /** Predicted click-through rate, 0..1. */
    private double predictedCtr;

    /** Comma-separated user segments, or "*" for everyone. */
    private String targetSegments;

    /** Comma-separated placements, e.g. "app_store_search,news_feed". */
    private String placements;

    private long totalBudgetCents;

    private long spentCents;

    private long impressions;

    private long clicks;

    private boolean active = true;

    protected Campaign() {
        // for JPA
    }

    public Campaign(String advertiserId, String adId, String creativeUrl, long bidCents, double predictedCtr,
                    String targetSegments, String placements, long totalBudgetCents) {
        this.advertiserId = advertiserId;
        this.adId = adId;
        this.creativeUrl = creativeUrl;
        this.bidCents = bidCents;
        this.predictedCtr = predictedCtr;
        this.targetSegments = targetSegments;
        this.placements = placements;
        this.totalBudgetCents = totalBudgetCents;
    }

    public CampaignSnapshot toSnapshot() {
        return new CampaignSnapshot(id, advertiserId, adId, creativeUrl, bidCents, predictedCtr,
                split(targetSegments), split(placements), totalBudgetCents);
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Long getId() { return id; }
    public String getAdvertiserId() { return advertiserId; }
    public String getAdId() { return adId; }
    public String getCreativeUrl() { return creativeUrl; }
    public long getBidCents() { return bidCents; }
    public double getPredictedCtr() { return predictedCtr; }
    public String getTargetSegments() { return targetSegments; }
    public String getPlacements() { return placements; }
    public long getTotalBudgetCents() { return totalBudgetCents; }
    public long getSpentCents() { return spentCents; }
    public long getImpressions() { return impressions; }
    public long getClicks() { return clicks; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
