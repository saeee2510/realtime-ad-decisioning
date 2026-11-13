package com.saee.addecision.api;

public record DecisionResponse(
        String requestId,
        long campaignId,
        String adId,
        String creativeUrl,
        double expectedValue) {
}
