package com.saee.addecision.seed;

import com.saee.addecision.model.Campaign;
import com.saee.addecision.repository.CampaignRepository;
import com.saee.addecision.service.CampaignCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeds deterministic demo campaigns (fixed random seed) on first start. */
@Component
@ConditionalOnProperty(name = "ads.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    static final List<String> SEGMENTS = List.of("sports", "gaming", "news", "music", "finance", "travel");
    static final List<String> PLACEMENTS = List.of("app_store_search", "app_store_today", "news_feed");

    private final CampaignRepository repository;
    private final CampaignCache cache;
    private final int count;

    public DataSeeder(CampaignRepository repository, CampaignCache cache, @Value("${ads.seed.count:200}") int count) {
        this.repository = repository;
        this.cache = cache;
        this.count = count;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            Random random = new Random(42);
            List<Campaign> campaigns = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String segments = random.nextInt(10) == 0
                        ? "*"
                        : pick(SEGMENTS, random) + "," + pick(SEGMENTS, random);
                campaigns.add(new Campaign(
                        "adv-" + (i % 40),
                        "ad-" + i,
                        "https://cdn.example.com/creatives/" + i + ".png",
                        50 + random.nextInt(451),               // bid: $0.50 - $5.00 per click
                        0.005 + random.nextDouble() * 0.075,    // predicted CTR: 0.5% - 8%
                        segments,
                        pick(PLACEMENTS, random),
                        10_000 + random.nextInt(90_001)));      // budget: $100 - $1,000
            }
            repository.saveAll(campaigns);
            log.info("Seeded {} campaigns", count);
        }
        cache.refreshFromDatabase();
        cache.syncFromRedis();
    }

    private static String pick(List<String> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }
}
