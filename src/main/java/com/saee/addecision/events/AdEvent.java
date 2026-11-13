package com.saee.addecision.events;

import com.saee.addecision.model.CampaignSnapshot;

/**
 * Event published to Kafka. The event id is derived from the request id,
 * so a repeated click for the same request is charged only once.
 */
public record AdEvent(
        String eventId,
        EventType type,
        String requestId,
        long campaignId,
        String adId,
        long costCents,
        long timestampMs) {

    public static AdEvent impression(String requestId, CampaignSnapshot campaign) {
        return new AdEvent("imp:" + requestId, EventType.IMPRESSION, requestId, campaign.id(), campaign.adId(),
                0L, System.currentTimeMillis());
    }

    public static AdEvent click(String requestId, CampaignSnapshot campaign) {
        return new AdEvent("click:" + requestId, EventType.CLICK, requestId, campaign.id(), campaign.adId(),
                campaign.bidCents(), System.currentTimeMillis());
    }
}
