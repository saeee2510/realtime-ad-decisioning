package com.saee.addecision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saee.addecision.model.Campaign;
import com.saee.addecision.model.CampaignSnapshot;
import com.saee.addecision.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two-level campaign cache so the decision path never touches PostgreSQL:
 * <ul>
 *   <li>refreshFromDatabase: PostgreSQL -> Redis (shared by all instances), every few seconds</li>
 *   <li>syncFromRedis: Redis -> local memory, every second; decisions read the local copy</li>
 * </ul>
 */
@Component
public class CampaignCache {

    private static final Logger log = LoggerFactory.getLogger(CampaignCache.class);
    static final String ACTIVE_KEY = "campaigns:active";
    private static final TypeReference<List<CampaignSnapshot>> LIST_TYPE = new TypeReference<>() { };

    private final CampaignRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final BudgetTracker budgetTracker;

    private volatile List<CampaignSnapshot> active = List.of();
    private volatile Map<Long, CampaignSnapshot> byId = Map.of();

    public CampaignCache(CampaignRepository repository, StringRedisTemplate redis, ObjectMapper mapper,
                         BudgetTracker budgetTracker) {
        this.repository = repository;
        this.redis = redis;
        this.mapper = mapper;
        this.budgetTracker = budgetTracker;
    }

    @Scheduled(initialDelay = 1_000, fixedDelayString = "${ads.cache.refresh-ms:5000}")
    public void refreshFromDatabase() {
        List<Campaign> campaigns = repository.findByActiveTrue();
        campaigns.forEach(c -> budgetTracker.initIfAbsent(c.getId(), c.getSpentCents()));
        List<CampaignSnapshot> snapshots = campaigns.stream().map(Campaign::toSnapshot).toList();
        try {
            redis.opsForValue().set(ACTIVE_KEY, mapper.writeValueAsString(snapshots));
        } catch (JsonProcessingException e) {
            log.error("Could not serialize campaign snapshot", e);
        }
    }

    @Scheduled(initialDelay = 1_500, fixedDelayString = "${ads.cache.sync-ms:1000}")
    public void syncFromRedis() {
        String json = redis.opsForValue().get(ACTIVE_KEY);
        if (json == null) {
            return;
        }
        try {
            List<CampaignSnapshot> snapshots = mapper.readValue(json, LIST_TYPE);
            active = List.copyOf(snapshots);
            byId = snapshots.stream().collect(Collectors.toUnmodifiableMap(CampaignSnapshot::id, Function.identity()));
        } catch (JsonProcessingException e) {
            log.warn("Could not parse campaign snapshot from Redis; keeping previous copy", e);
        }
    }

    public List<CampaignSnapshot> active() {
        return active;
    }

    public Optional<CampaignSnapshot> find(long campaignId) {
        return Optional.ofNullable(byId.get(campaignId));
    }
}
