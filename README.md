# Ad Decision Service

A low-latency service that decides which ad to show for a request: it filters campaigns by targeting, drops campaigns that are out of budget, ranks the rest by expected value, and records impressions and clicks through Kafka.

**Stack:** Java 21 · Spring Boot 3.3 · Redis · Kafka · PostgreSQL · JUnit 5 · Testcontainers · k6

## Architecture

```
            POST /v1/decide
client ───────────────────────► DecisionController
                                     │
                                     ▼
                              DecisionService
            ┌────────────────────────┼─────────────────────────┐
            ▼                        ▼                         ▼
  CampaignCache (in memory)   BudgetTracker (Redis MGET)   EventPublisher ──► Kafka "ad-events"
            ▲                                                                      │
            │ sync every 1s                                                        ▼
          Redis  ◄── refresh every 5s ── PostgreSQL ◄─────────────────────  SpendConsumer
                                                       impressions, clicks, spend
```

**Hot path per request (no database calls):**
1. Filter active campaigns by placement and user segment, using an in-memory snapshot.
2. Read spend for all eligible campaigns in **one Redis MGET**.
3. Drop campaigns that can't afford another click, then rank by **bid × predicted CTR** (ties go to the lowest campaign id).
4. Publish an impression event to Kafka asynchronously and return the ad.

**Write path:** `POST /v1/events/click` publishes a click event. `SpendConsumer` charges the bid to PostgreSQL and to the Redis spend counter. Kafka delivers at least once, so events are **de-duplicated by event id** (derived from the request id); a repeated click is charged only once.

## Run it

Requirements: Java 21, Maven 3.9+, Docker, and (for load tests) [k6](https://k6.io).

```bash
docker compose up -d          # PostgreSQL, Redis, Kafka
mvn spring-boot:run           # seeds 200 demo campaigns on first start
```

Try it:

```bash
curl -s -X POST localhost:8080/v1/decide \
  -H 'Content-Type: application/json' \
  -d '{"userSegment":"sports","placement":"app_store_search","context":{}}'

# Use the requestId and campaignId from the response:
curl -i -X POST localhost:8080/v1/events/click \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"<requestId>","campaignId":<campaignId>}'
```

`204 No Content` means no campaign matched the request.

## Tests

```bash
mvn test
```

- `DecisionEngineTest`: unit tests for ranking, targeting, wildcard segments, budget exhaustion and tie-breaking.
- `DecisionFlowIntegrationTest`: end-to-end against real PostgreSQL, Redis and Kafka containers (Docker must be running). It checks the full decide → click → spend flow and that a duplicate click is charged exactly once.

## Load test

With the service running:

```bash
k6 run load/decide.js                    # 500 req/s for 60s
k6 run -e RATE=1000 load/decide.js       # 1,000 req/s
```

The script fails if p99 latency exceeds 20 ms or more than 1% of requests error. Record your results here:

| Rate (req/s) | p50 | p95 | p99 | Error rate | Machine |
|---|---|---|---|---|---|
| 500 | | | | | |
| 1000 | | | | | |

## Design decisions

- **Two-level cache.** PostgreSQL is the source of truth; a snapshot is pushed to Redis every 5 s so all instances share it, and each instance pulls it into memory every second. Decisions read memory, so targeting costs no network calls. The trade-off is up to ~6 s of staleness for campaign changes.
- **Spend in Redis, not the database.** Budget checks need fresh numbers on every request; one MGET is far cheaper than a SQL query.
- **Async events.** The response never waits on Kafka or PostgreSQL.
- **Idempotent consumer.** At-least-once delivery means duplicates happen; a Redis `SETNX` on the event id makes charging safe to retry.

## Limitations and next steps

- Budgets are lifetime totals; a real system would reset daily and pace spend through the day.
- Under concurrency, several requests can win the last of a budget before the click is charged (a small overspend). Reserving budget atomically with a Lua script would close that gap.
- The event id is marked as seen before the database update, so a crash between the two can drop an event. A transactional outbox, or storing processed ids in PostgreSQL in the same transaction, would fix it.
- Impression updates hit one row per campaign; batching them would reduce database load at high traffic.
- Predicted CTR is a static number per campaign; the natural next step is a model that predicts it per request.
# realtime-ad-decisioning
