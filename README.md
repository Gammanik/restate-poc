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

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start services
./gradlew :httpbin-proxy:bootRun &  # Port 8091 (external services mock)
./gradlew :los-service:bootRun &    # Port 8000 (LOS FSM - internal only)
./gradlew :restate-impl:run &       # Port 9000 (Restate workflow endpoint)
# OR
./gradlew :temporal-impl:run &      # Port 9001 (Temporal workflow endpoint)

# Wait 60s for JVM warmup
sleep 60

# 3. Smoke test (Restate)
curl -X POST http://localhost:9000/api/applications \
  -H 'Content-Type: application/json' \
  -d @payload.json

# Should return 200 + {"status":"approved",...} after ~120ms

# Or for Temporal:
# curl -X POST http://localhost:9001/api/applications ...

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
Restate      3000    100.0%     130.9ms      166.6ms      228.0ms     49.90/s
Temporal      TBD      TBD%       TBDms        TBDms        TBDms        TBD
```

### Test @ 100 RPS, 60s duration, 30s warmup

```
Engine     Requests  Success  Latency p50  Latency p95  Latency p99  Throughput
-------------------------------------------------------------------------------
Restate      6000    100.0%     135.4ms      167.3ms      240.7ms     99.78/s
Temporal      TBD      TBD%       TBDms        TBDms        TBDms        TBD
```

**Analysis** (Restate results):
- Consistent latency across load levels: ~131-135ms p50, ~167ms p95
- Near-theoretical minimum: 105ms (7×15ms external) + 30-40ms orchestration
- 100% success rate at both 50 and 100 RPS
- Latencies align with expectation: minimal orchestration overhead over external service calls

These results demonstrate the efficiency of synchronous workflow execution with properly tuned HTTP server threads (100 concurrent)

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
- **Framework**: Spring Boot 3.4 (LOS service)
- **Orchestration**: Temporal SDK 1.26, Direct HTTP implementation (Restate)
- **HTTP**: OkHttp 4.12, JDK HttpServer
- **Load Testing**: Vegeta

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
- Restate implementation is simplified (no Restate SDK, direct HTTP) for POC purposes

## License

MIT
