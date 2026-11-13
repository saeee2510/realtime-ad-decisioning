package com.saee.addecision;

import com.saee.addecision.api.ClickRequest;
import com.saee.addecision.api.DecisionRequest;
import com.saee.addecision.api.DecisionResponse;
import com.saee.addecision.model.Campaign;
import com.saee.addecision.repository.CampaignRepository;
import com.saee.addecision.service.CampaignCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test against real PostgreSQL, Redis and Kafka containers (requires Docker).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ads.seed.enabled=false",
                "ads.cache.refresh-ms=600000",
                "ads.cache.sync-ms=600000"
        })
@Testcontainers
class DecisionFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    CampaignRepository repository;

    @Autowired
    CampaignCache cache;

    private Campaign high;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        high = repository.save(new Campaign("adv-1", "ad-high", "https://cdn.example.com/high.png",
                300, 0.05, "sports", "app_store_search", 10_000));
        repository.save(new Campaign("adv-2", "ad-low", "https://cdn.example.com/low.png",
                100, 0.05, "sports", "app_store_search", 10_000));
        repository.save(new Campaign("adv-3", "ad-other-placement", "https://cdn.example.com/other.png",
                1_000, 0.10, "sports", "news_feed", 10_000));
        cache.refreshFromDatabase();
        cache.syncFromRedis();
    }

    @Test
    void returnsHighestValueEligibleAd() {
        ResponseEntity<DecisionResponse> response = decide("sports", "app_store_search");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().adId()).isEqualTo("ad-high");
    }

    @Test
    void returnsNoContentWhenNothingMatches() {
        ResponseEntity<DecisionResponse> response = decide("gaming", "app_store_search");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void duplicateClickIsChargedExactlyOnce() {
        DecisionResponse decision = decide("sports", "app_store_search").getBody();
        assertThat(decision).isNotNull();
        ClickRequest click = new ClickRequest(decision.requestId(), decision.campaignId());

        assertThat(rest.postForEntity("/v1/events/click", click, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        rest.postForEntity("/v1/events/click", click, Void.class); // same click sent twice

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(repository.findById(high.getId()).orElseThrow().getSpentCents()).isEqualTo(300));
        // Give the duplicate time to arrive, then confirm it was ignored.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Campaign campaign = repository.findById(high.getId()).orElseThrow();
            assertThat(campaign.getSpentCents()).isEqualTo(300);
            assertThat(campaign.getClicks()).isEqualTo(1);
        });
    }

    private ResponseEntity<DecisionResponse> decide(String segment, String placement) {
        return rest.postForEntity("/v1/decide", new DecisionRequest(segment, placement, Map.of()),
                DecisionResponse.class);
    }
}
