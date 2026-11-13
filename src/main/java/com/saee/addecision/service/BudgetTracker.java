package com.saee.addecision.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spend counters and event de-duplication in Redis.
 * One MGET per decision reads spend for every eligible campaign.
 */
@Component
public class BudgetTracker {

    private static final String SPEND_PREFIX = "spend:";
    private static final String SEEN_PREFIX = "event:";
    private static final Duration DEDUP_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public BudgetTracker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Map<Long, Long> spentFor(List<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = campaignIds.stream().map(id -> SPEND_PREFIX + id).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        Map<Long, Long> result = new HashMap<>(campaignIds.size() * 2);
        for (int i = 0; i < campaignIds.size(); i++) {
            String value = values == null ? null : values.get(i);
            result.put(campaignIds.get(i), value == null ? 0L : Long.parseLong(value));
        }
        return result;
    }

    public void addSpend(long campaignId, long cents) {
        redis.opsForValue().increment(SPEND_PREFIX + campaignId, cents);
    }

    /** Seeds the Redis counter from the database value (e.g. after a Redis restart). */
    public void initIfAbsent(long campaignId, long spentCents) {
        redis.opsForValue().setIfAbsent(SPEND_PREFIX + campaignId, Long.toString(spentCents));
    }

    /** Returns true the first time an event id is seen, false for duplicates. */
    public boolean firstTimeSeen(String eventId) {
        Boolean isNew = redis.opsForValue().setIfAbsent(SEEN_PREFIX + eventId, "1", DEDUP_TTL);
        return Boolean.TRUE.equals(isNew);
    }
}
