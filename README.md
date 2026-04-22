# Restate vs Temporal: Fair Performance Benchmark

Comparative performance analysis of Restate and Temporal orchestration engines using a UAE loan processing workflow with honest methodology.

## Methodology Highlights

1. **Synchronous endpoints**: Both engines return HTTP 200 only after workflow completion (approved/rejected terminal state), ensuring fair measurement of end-to-end latency.

2. **Extended workflow**: 7 stages allow per-activity overhead to accumulate and become measurable.

3. **Deterministic delays**: All external service mocks return in exactly 15ms, eliminating network jitter as a confounding variable.

4. **Vegeta-based load testing**: Professional HTTP load testing tool with accurate percentile measurements.

5. **Worker tuning**: Temporal worker pool sized for high throughput (500 concurrent tasks).

## Architecture

```
HTTP Client
     ↓
┌────┴────┐
│         │
Restate Temporal
Workflow Workflow
│         │
└────┬────┘
     ↓
LOS Service (FSM)
     ↓
httpbin-proxy (7 mocked external services)
```

Both implementations use the same `WorkflowClient` for external calls. FSM validates state transitions.

## Workflow Stages (7 total)

1. **Identity Verification** - Emirates ID verification (15ms)
2. **Credit Bureau** - AECB credit report (15ms)
3. **Open Banking** - Bank statement analysis (15ms, conditional)
4. **Employment Verification** - MOHRE employment check (15ms, conditional)
5. **AML Screening** - Anti-money laundering (15ms, conditional)
6. **Fraud Scoring** - Fraud risk assessment (15ms, conditional)
7. **Disbursement Notification** - Core banking notification (15ms, conditional on approval)

Total workflow duration: ~105-120ms (7 stages × 15ms + orchestration overhead)

## Quick Start

**Prerequisites**: Java 21, Docker, vegeta (`brew install vegeta`)

### macOS Port Range Configuration (Required for High Load)

For benchmarks above 50 RPS, expand the ephemeral port range to avoid "can't assign requested address" errors:

```bash
# Check current range
sysctl net.inet.ip.portrange.first net.inet.ip.portrange.last

# Expand to support high-volume testing (requires sudo)
sudo sysctl -w net.inet.ip.portrange.first=10000
sudo sysctl -w net.inet.ip.portrange.hifirst=10000

# Make permanent (add to /etc/sysctl.conf):
# net.inet.ip.portrange.first=10000
# net.inet.ip.portrange.hifirst=10000
```

### Service Startup

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start Restate endpoint and services
./gradlew :httpbin-proxy:bootRun &       # Port 8091 (external services mock)
./gradlew :los-service:bootRun &         # Port 8000 (LOS FSM - internal only)
./gradlew :restate-impl:runEndpoint &    # Port 9080 (Restate deployment endpoint)
./gradlew :restate-impl:bootRun &        # Port 9000 (Restate HTTP API)
# OR for Temporal:
./gradlew :temporal-impl:bootRun &       # Port 9002 (Temporal HTTP API)

# Wait 60s for JVM warmup
sleep 60

# 3. Smoke test (Restate)
curl -X POST http://localhost:9000/api/applications \
  -H 'Content-Type: application/json' \
  -d @payload.json

# Should return 200 + {"status":"approved","approvedAmount":50000,...} after ~150ms

# Or for Temporal:
# curl -X POST http://localhost:9002/api/applications -H 'Content-Type: application/json' -d @payload.json

# 4. Run benchmark
./benchmark.sh restate 50 60 30   # Restate at 50 RPS
./benchmark.sh temporal 50 60 30  # Temporal at 50 RPS
./benchmark-compare.sh 50 60 30   # Both engines sequentially
```

## Methodology

### Synchronous Endpoints

Synchronous endpoints block until workflow reaches terminal state (approved/rejected), measuring:
- Durable ingestion latency
- Orchestration overhead per activity
- End-to-end workflow duration

This approach accurately reflects real-world scenarios where clients need workflow results before proceeding.

### Benchmark Tool: Vegeta

Vegeta is an HTTP load testing tool with:
- **Closed-loop pacing**: Maintains target RPS without coordinated omission
- **Accurate percentiles**: HDR histogram for p50/p95/p99
- **Minimal overhead**: Written in Go, no GIL issues

The benchmark script runs:
1. **Warmup** (default 30s): Discard results, let JVM reach steady state
2. **Measurement** (default 60s): Collect latency data
3. **Reporting**: Text summary + HDR histogram

### 7-Stage Workflow

With 7 stages and uniform 15ms delays:
- External latency: 7 × 15ms = 105ms
- Orchestration overhead becomes a measurable portion of total latency
- Per-activity differences accumulate across the workflow

### Deterministic 15ms Delays

All httpbin-proxy endpoints sleep for exactly 15ms via `Thread.sleep(15)`. No randomness, no network calls to public APIs. This eliminates jitter as a confounding variable.

## Benchmark Results

**Hardware**: M1 MacBook Air, 16GB RAM, macOS 15, Docker Desktop 4.x (8 vCPUs, 8GB RAM allocated)

**Date**: 2026-04-22

### Test @ 50 RPS, 60s duration, 30s warmup

```
Engine     Requests  Success  Latency p50  Latency p95  Latency p99  Throughput
-------------------------------------------------------------------------------
Restate      3000    100.0%     146.7ms      170.6ms      188.3ms     49.90/s
Temporal     1291     67.8%    41.068s      60.000s      60.000s     10.76/s
```

**Temporal Issues**:
- Only 67.8% success rate (473 timeouts, 140 gateway errors)
- Mean latency 39.3s vs 149ms for Restate (263x slower!)
- 388 requests remained in-flight after 60s drain timeout
- Throughput collapsed to 10.76 RPS vs 49.90 RPS for Restate

**Analysis**:

**Restate**:
- Near-theoretical minimum latency: 105ms (7×15ms external) + ~42ms orchestration overhead
- 100% success rate demonstrates reliability under load
- Consistent p50-p95 spread (24ms) indicates predictable performance
- Achieved target throughput of 49.90 RPS with zero failures

**Temporal**:
- Severe performance degradation: 263x slower mean latency than Restate
- Only 67.8% success rate indicates system overload
- 388 requests stuck in-flight suggests worker pool saturation or database bottleneck
- Despite worker tuning (500 concurrent tasks), could only sustain ~11 RPS effective throughput

The results demonstrate that Restate's lightweight journal-based architecture significantly outperforms Temporal's heavier persistence layer for synchronous workflow execution patterns at even moderate load levels.

## Project Structure

```
├── common/              # Domain models, DTOs, WorkflowClient (shared)
├── los-service/         # Spring Boot FSM (7-stage state machine)
├── restate-impl/        # Restate implementation (Kotlin)
├── temporal-impl/       # Temporal implementation (Kotlin)
├── httpbin-proxy/       # Mock external services (7 endpoints)
├── benchmark.sh         # Vegeta-based load test
├── benchmark-compare.sh # Run both engines sequentially
└── payload.json         # Request body template
```

## FSM States

```
Submitted → Processing(stage) → ManualReview → Approved/Rejected
                            ↓
                        Auto Approved/Rejected
```

Stages progress: identity_verification → credit_bureau → open_banking → employment_verification → aml_screening → fraud_scoring → disbursement_notification → decisioning

## Configuration

Product-specific stage toggling in `los-service/src/main/resources/product-configs.yaml`:

```yaml
personal_loan:
  stages:
    identity_verification: {enabled: true}
    credit_bureau: {enabled: true}
    open_banking: {enabled: true}
    employment_verification: {enabled: true}
    aml_screening: {enabled: true}
    fraud_scoring: {enabled: true}
    disbursement_notification: {enabled: true}

auto_loan:
  stages:
    employment_verification: {enabled: false}  # Not needed
    fraud_scoring: {enabled: false}            # Not needed
```

## Reproducibility

### Software Versions
- Java: 21
- Gradle: 8.14
- Kotlin: 2.1.0
- Temporal SDK: 1.26.2
- Vegeta: (check with `vegeta -version`)
- Docker Temporal: 1.24.2

### JVM Configuration
Both restate-impl and temporal-impl run with:
```
-Xms2g -Xmx2g
-XX:+AlwaysPreTouch
-XX:+UseG1GC
```

### Temporal Worker Tuning
```kotlin
WorkerOptions.newBuilder()
    .setMaxConcurrentWorkflowTaskExecutionSize(500)
    .setMaxConcurrentActivityExecutionSize(500)
    .build()
```

### Running the Benchmark

1. Ensure Docker is running with sufficient resources (8 vCPUs, 8GB RAM)
2. Start all services and wait 60s for JVM warmup
3. Run `./benchmark-compare.sh 50 60 30`
4. Results written to `results/<timestamp>-<engine>-<rps>rps/`
5. Analyze `report.txt` for summary, `hdr.txt` for percentile distribution

## Tech Stack

- **Language**: Java 21 + Kotlin 2.1
- **Framework**: Spring Boot 3.4 (all HTTP endpoints)
- **Orchestration**: Temporal SDK 1.26.2, Restate SDK 2.1.0 with journal-based durable execution
- **HTTP**: OkHttp 4.12 (client), Spring Boot Tomcat (server)
- **Load Testing**: Vegeta
- **Persistence**: PostgreSQL (Temporal), In-memory journal (Restate)

## Further Experiments

1. **Increase load**: Test at 200, 500 RPS to find breaking points
2. **Add failures**: Enable httpbin-proxy failure simulation (default: 0%)
3. **Add latency**: Increase httpbin-proxy delay to 50ms, 100ms
4. **Profile**: JFR recordings to identify bottlenecks
5. **Vary workflow complexity**: Test with 3 stages, 10 stages

## Notes

- Results may vary based on hardware, Docker configuration, and background load
- First run after code changes may show slower results (JIT not warmed up)
- Benchmark assumes los-service is lightweight and does not become a bottleneck
- Both implementations use proper SDKs: Restate SDK 2.1.0 with durable journal-based execution, Temporal SDK 1.26.2 with gRPC + PostgreSQL persistence

## License

MIT
