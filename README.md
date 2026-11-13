# Realtime Ad Decisioning

A low-latency service that decides which ad to show for a request: it filters campaigns by targeting, drops campaigns that are out of budget, ranks the rest by expected value, and records impressions and clicks through Kafka.

**Stack:** Java 21 · Spring Boot 3.3 · Redis · Kafka · PostgreSQL · JUnit 5 · Testcontainers · k6

## Highlights

- **5.4 ms p99 at 500 requests/sec** (0.96 ms median) with zero errors; sustains 1,000 requests/sec with zero errors.
- **No database calls on the hot path:** targeting runs on an in-memory snapshot, and budget checks take one Redis MGET.
- **No overspend:** clicks are charged through an atomic Redis Lua script, verified by 200 concurrent charges against a 10-click budget.
- **Exactly-once billing on an at-least-once pipeline:** duplicate Kafka events and repeated clicks are charged once.
- **11 tests:** unit tests for the decision logic, plus integration tests against real PostgreSQL, Redis and Kafka containers.

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

**Write path:** `POST /v1/events/click` publishes a click event. `SpendConsumer` charges the bid through a **Redis Lua script** that de-duplicates the event and checks the budget in one atomic step, then records the click in PostgreSQL. A campaign can never be billed past its budget, and a repeated click is charged only once. Clicks that arrive after the budget runs out are counted as `unbilled_clicks`.

## Run it

Requirements: Java 21, Maven 3.9+, Docker, and (for load tests) [k6](https://k6.io).

```bash
git clone https://github.com/saeee2510/realtime-ad-decisioning.git
cd realtime-ad-decisioning
docker compose up -d          # PostgreSQL (port 55432), Redis, Kafka
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
- `BudgetRaceIntegrationTest`: fires 200 concurrent charges at the Lua script and 100 parallel clicks through the full API and Kafka path, against a budget that covers exactly 10 clicks. Both prove spend lands on the budget exactly, with no overspend.

## Load test

With the service running:

```bash
k6 run load/decide.js                    # 500 req/s for 60s
k6 run -e RATE=1000 load/decide.js       # 1,000 req/s
```

The script fails if p99 latency exceeds 20 ms or more than 1% of requests error.

### Results

| Rate (req/s) | p50 | p95 | p99 | Error rate | Machine |
|---|---|---|---|---|---|
| 500 | 0.96 ms | 1.64 ms | 5.41 ms | 0% | MacBook Air, all services local |
| 1000 | 0.88 ms | 4.08 ms | 52.3 ms | 0% | MacBook Air, all services local |

Measured after a 20-second warm-up, with the load generator, service and infrastructure all on one laptop. At 1,000 req/s the tail rises because k6, the JVM, PostgreSQL, Redis and Kafka compete for the same CPU, and every impression writes to the database.

## Mac setup notes

- If port 5432 is already used by a local PostgreSQL, Docker can't start the container (status stays `Created`). This project maps PostgreSQL to **55432** to avoid that.
- If Testcontainers reports *"Could not find a valid Docker environment"* with recent Docker Desktop: create `~/.testcontainers.properties` with `docker.host=unix:///Users/<you>/.docker/run/docker.sock`, create `~/.docker-java.properties` with `api.version=1.44`, enable **Settings → Advanced → Allow the default Docker socket to be used**, and run `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` before `mvn test`.

## Design decisions

- **Two-level cache.** PostgreSQL is the source of truth; a snapshot is pushed to Redis every 5 s so all instances share it, and each instance pulls it into memory every second. Decisions read memory, so targeting costs no network calls. The trade-off is up to ~6 s of staleness for campaign changes.
- **Spend in Redis, not the database.** Budget checks need fresh numbers on every request; one MGET is far cheaper than a SQL query.
- **Async events.** The response never waits on Kafka or PostgreSQL.
- **Budget cap at charge time.** The decision path only reads spend, so near the end of a budget several requests can win the same last dollars, and their clicks arrive later. The first version charged every click and could overspend; now each click is charged only if it fits the remaining budget.
- **Atomic check-and-charge.** Events are keyed by campaign, so one consumer thread normally handles a campaign's clicks in order. The charge is still done as a single Lua script so it stays correct when that assumption breaks: during a partition rebalance, with several service instances, or if the partition key changes. `BudgetRaceIntegrationTest` hits the script from 32 threads at once to prove it.
- **Idempotent consumer.** At-least-once delivery means duplicates happen; the same Lua script records the event id, so charging is safe to retry.

## Limitations and next steps

- Budgets are lifetime totals; a real system would reset daily and pace spend through the day.
- The decision path reads spend without reserving it, so an ad can still be shown when its budget is nearly gone; the resulting clicks are recorded as unbilled rather than overcharged.
- Redis is charged before the database update, so a crash between the two can leave PostgreSQL behind Redis. A transactional outbox, or storing processed ids in PostgreSQL in the same transaction, would fix it.
- Impression updates hit one row per campaign; batching them would reduce database load at high traffic.
- Predicted CTR is a static number per campaign; the natural next step is a model that predicts it per request.
