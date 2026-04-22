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

**Environment**: M1 MacBook Air, 16GB RAM, macOS 15, Docker Desktop 4.x (8 vCPUs, 8GB RAM)

**Date**: 2026-04-22

**Important**: These results reflect a local development setup with Docker-based Temporal (Postgres backend), not production Temporal Cloud or optimized on-premise deployment. See "Limitations" section below.

### Restate @ 50 RPS

```
Requests: 3000
Success:  100.0%
Latency:  p50=146.8ms, p95=254.0ms, p99=653.5ms
Throughput: 49.88/s
In-flight after drain: 0
```

### Temporal @ Various RPS (After Application-Level Optimizations)

After applying application-level optimizations (HTTP connection pooling, thread pool sizing, timeout tuning), we observed the following results:

| RPS | Success Rate | p50 Latency | p90 Latency | p95 Latency | Throughput | In-Flight |
|-----|--------------|-------------|-------------|-------------|------------|-----------|
| 50  | 80.68%       | 32.3s       | 60s         | 60s         | 14.70/s    | 216       |
| 20  | 78.67%       | 7.2s        | 30.1s       | 30.2s       | 10.48/s    | 167       |
| 15  | 90.44%       | 15.5s       | 29.8s       | 30.1s       | 9.46/s     | 49        |
| **10**  | **100%**     | **1.5s**    | **8.2s**    | **8.9s**    | **8.88/s** | **0**     |

### Application-Level Optimizations Applied

1. **HTTP Client**: Singleton `OkHttpClient` with connection pooling (200 connections, 5min keep-alive)
2. **HTTP Server Thread Pool**: Increased from 100 to 500 threads
3. **HTTP Server Backlog Queue**: Increased from default (~50) to 1000
4. **Workflow Execution Timeout**: Increased from 30s to 120s
5. **Activity Timeouts**: StartToClose (60s), ScheduleToClose (90s), ScheduleToStart (30s)
6. **Benchmark URL**: Fixed from `localhost` to `127.0.0.1` for vegeta DNS compatibility

### Observed Results Summary (Local Setup)

| Metric | Restate @ 50 RPS | Temporal @ 10 RPS |
|--------|------------------|-------------------|
| Success Rate | 100% | 100% |
| p50 Latency | 146.8ms | 1,544ms |
| p95 Latency | 254ms | 8,933ms |
| p99 Latency | 653ms | 10,865ms |
| Throughput | 49.88/s | 8.88/s |

**Note**: At 50 RPS, the local Temporal setup showed 80.68% success rate with 216 in-flight workflows remaining after 60s drain period. At 10 RPS, we achieved 100% success rate.

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
- **Restate Implementation**: Uses `@Workflow` annotation (not `@Service`) to ensure workflow invocations are retained in Restate UI after completion, providing UI parity with Temporal for observability and debugging

## What We Measured

This benchmark evaluates Restate and Temporal for a 7-stage UAE loan processing workflow with synchronous HTTP endpoints. Both implementations use identical external HTTP clients, deterministic 15ms delays, and proper SDK usage.

### Methodology Strengths

✅ **Synchronous endpoints** for both (HTTP 200 only after workflow completion)
✅ **Deterministic 15ms delays** on all external calls (no network jitter)
✅ **Identical external HTTP client** (shared `WorkflowClient.java`)
✅ **Proper SDK usage**: Restate SDK 2.1.0, Temporal SDK 1.26.2
✅ **Worker tuning**: Temporal configured with 500 concurrent workflow/activity slots
✅ **Application-level optimizations**: HTTP connection pooling, thread pool sizing, timeout tuning
✅ **Professional tooling**: Vegeta load testing with HDR percentiles

### Observed Behavior in This Setup

**Restate (local single-node)**:
- Sustained 50 RPS with 100% success rate
- p50 latency: 147ms (105ms external calls + ~42ms orchestration)
- p99 latency: 653ms (occasional GC/warmup spikes)
- Zero in-flight workflows after completion

**Temporal (Docker Postgres backend)**:
- At 10 RPS: 100% success, p50=1.5s, p95=8.9s
- At 50 RPS: 80.68% success, p50=32s, 216 workflows stuck in-flight after 60s drain
- Observed gRPC warnings in logs: "Workflow task not found"
- Postgres CPU remained low (0.81%), suggesting I/O or lock contention rather than CPU saturation

### Honest Limitations of This POC

**Infrastructure**:
- Single M1 MacBook, Docker Desktop (not production-grade)
- Temporal on Docker auto-setup with Postgres (not Cassandra or Temporal Cloud)
- No distributed deployment testing
- No infrastructure-level tuning (Postgres config, Docker resources)

**Test Coverage**:
- Only tested synchronous request/response pattern
- No crash recovery or failure injection
- No long-running workflow scenarios (hours/days)
- Did not measure Restate's upper throughput limit
- httpbin-proxy and LOS service are shared bottlenecks (not isolated per engine)

**Known Code Issues Not Addressed**:
- httpbin-proxy uses blocking `Thread.sleep(15)` instead of async delay
- Postgres uses default Docker configuration (not tuned for writes)
- No profiling to identify specific bottlenecks in Temporal setup

**Scope**:
- This POC measures one specific workload pattern on one specific setup
- Results do not represent production Temporal Cloud performance
- Temporal's strengths in long-running workflows, audit trails, and ecosystem tooling are not tested

### Trade-offs Observed

**Restate (in this test)**:
- Low latency for synchronous workflows
- Simple deployment model (single binary)
- Good performance out-of-the-box on laptop
- Limited: smaller ecosystem, newer project, less production battle-testing

**Temporal (in this test)**:
- Higher latency in local Docker Postgres setup
- Rich ecosystem and tooling (SDKs, UI, CLI)
- Proven at scale in production environments (when properly deployed)
- Excellent for long-running workflows and audit trails

### Recommendations

**Consider Restate when**:
- Primary workload is synchronous request/response (seconds to minutes)
- Low latency is critical (sub-200ms p50)
- Deploying on modern cloud infrastructure
- Comfortable with newer, smaller ecosystem

**Consider Temporal when**:
- Long-running workflows (hours to weeks)
- Need strong audit trail and event replay capabilities
- Require rich ecosystem (many SDKs, integrations, community support)
- Proven production track record is important

**Next Steps for Fair Evaluation**:
1. Test Temporal Cloud or properly tuned on-premise cluster
2. Tune Postgres for write-heavy workloads
3. Profile both implementations to identify bottlenecks
4. Test async workflow patterns (Temporal's sweet spot)
5. Add failure injection and recovery testing
6. Measure Restate's throughput ceiling
7. Test long-running workflow scenarios

## License

MIT
