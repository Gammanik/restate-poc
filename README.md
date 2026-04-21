# Restate vs Temporal Performance Benchmark

Comparative performance analysis of Restate and Temporal orchestration engines using a UAE loan processing workflow.

## Architecture

```
Common HTTP Client (WorkflowClient)
           ↓
   ┌───────┴───────┐
   │               │
Restate         Temporal
Workflow        Workflow
   │               │
   └───────┬───────┘
           ↓
    LOS Service (FSM)
           ↓
   httpbin-proxy (AECB, Open Banking simulation)
```

**Key Design**: Single client implementation, two workflow engines. FSM in LOS validates state transitions.

## Quick Start

```bash
# 1. Infrastructure
docker compose up -d

# 2. Services
./gradlew :httpbin-proxy:bootRun &      # Port 8090
./gradlew :restate-impl:run &            # Port 9080
./gradlew :los-service:bootRun &         # Port 8000

# 3. Register Restate
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# 4. Test
./demo-script.sh happy-path
```

## Benchmark

**Duration-based RPS Testing**:
- Warmup period (10s) before measurement
- Duration-based testing (30s per RPS level)
- Rate limiting to target RPS
- Percentile latency (p50, p95, p99)
- Error rate tracking

```bash
# Quick test (30s at 100 RPS)
python3 benchmark.py

# Custom RPS level
python3 benchmark.py --rps 500 --duration 30

# Full stress test (10-15k RPS) + graphs
python3 benchmark.py --stress

# Shorter stress test (10s per level)
python3 benchmark.py --stress --duration 10
```

**Results**:
- CSV: `benchmark-results.csv` or `benchmark-stress-results.csv`
- Graphs: `benchmark-graph-latest.png` (9 comprehensive charts)
- Terminal: Real-time progress + comparison table

## Benchmark Results

### Test @ 100 RPS (30 seconds)

```
Engine       Actual RPS   Avg (ms)   p95 (ms)   p99 (ms)   Success Rate
------------------------------------------------------------------------
Restate           83.4        1.1        2.2        4.7       100.0%
Temporal          65.6     1116.5     3119.6     8939.0        98.0%
```

### Stress Test Results

| RPS   | Restate Avg | Temporal Avg | Restate Success | Temporal Success |
|-------|-------------|--------------|-----------------|------------------|
| 10    | 8 ms        | 25 ms        | 100%            | 100%             |
| 50    | 8 ms        | 789 ms       | 100%            | 100%             |
| 100   | 17 ms       | 1897 ms      | 100%            | 90.3%            |
| 500   | 86 ms       | crash        | 100%            | connection reset |

**Key Findings**:
- **Restate 1000x faster** at moderate load (1ms vs 1116ms @ 100 RPS)
- **Restate more stable**: 100% success rate up to 500 RPS
- **Temporal issues**:
  - High latency (1-9 seconds p99)
  - Starts dropping requests at 100 RPS (90% success)
  - Crashes at 500 RPS (connection reset)
- **Root cause**: Temporal blocks HTTP endpoint during workflow start (not async)
- **Same business logic**: Both use WorkflowClient for processing

## Project Structure

```
├── common/              # Domain, DTOs, HTTP client (shared)
├── los-service/         # Spring Boot + FSM (5 states)
├── restate-impl/        # Restate workflow (ctx.run)
├── temporal-impl/       # Temporal workflow + activities
├── httpbin-proxy/       # External services simulation
└── benchmark.py         # Benchmark + stress test + graphs
```

## FSM (5 states)

```
Submitted → Processing(stage) → ManualReview → Approved/Rejected
                            ↓
                        Auto Approved/Rejected
```

Stages: consent → aecb → open_banking → decisioning

## Product Configuration

`los-service/src/main/resources/product-configs.yaml`:

```yaml
personal_loan:
  stages:
    open_banking: enabled: true
  decision:
    auto_approve_score: 700
    auto_reject_score: 500

auto_loan:
  stages:
    open_banking: enabled: false  # Not needed for auto loans
  decision:
    auto_approve_score: 650
```

## UI

- **Restate**: http://localhost:9070 (ctx.run journal)
- **Temporal**: http://localhost:8088 (event history)

## Tech Stack

Java 21, Spring Boot 3.4, Restate SDK 2.0, Temporal SDK 1.26, OkHttp

## Performance Analysis

### Why such a difference?

**Restate (1-86 ms)**:
- HTTP endpoint returns immediately (async)
- Workflow executes in background via CompletableFuture
- No blocking on startup

**Temporal (1000+ ms)**:
- WorkflowClient.start() blocks until Temporal server confirmation
- Latency includes: serialization → gRPC → Postgres persistence → response
- Under high load, queue fills up → timeouts

### Recommendations

**Use Restate for**:
- Low latency HTTP API requirements (< 10ms)
- High RPS (500+ requests/sec)
- Simple workflows without complex state management
- Minimalist approach without heavy dependencies

**Use Temporal for**:
- Complex long-running workflows (hours/days)
- Full event history and replay required
- Critical reliability (battle-tested, production-ready)
- Workflow visualization in UI needed
- But requires optimization for high RPS (worker pool, batching)

**Next steps for Temporal optimization**:
1. Async workflow start: `WorkflowClient.start()` in separate thread
2. Increase worker pool (currently default)
3. Batch API calls (group workflow starts)
4. HTTP/2 for gRPC connections

See **[OPTIMIZATION.md](OPTIMIZATION.md)** for optimization details.

## Benchmark Graphs

The enhanced benchmark generates 11 comprehensive visualizations:

**Main Dashboard (9 charts)**:
1. Throughput: Actual vs Target RPS
2. Average Latency vs Load
3. Latency Percentiles (p50, p95, p99)
4. Error Rate vs Load
5. Success Rate vs Load
6. Latency Range (Min/Avg/Max)
7. Throughput Efficiency
8. Actual Throughput (Bar chart)
9. Performance Summary Table

**Detailed Analysis (2 charts)**:
10. Detailed Latency Breakdown (Bar chart)
11. Throughput vs Latency Trade-off (Scatter plot)

## Further Experiments

1. **Stress test**: `python3 benchmark.py --stress` (comprehensive graphs)
2. **Add failures**: Increase httpbin-proxy failure_rate → test retry logic
3. **Profiling**: JFR to identify bottlenecks
4. **Cloud deployment**: Deploy to k8s, test at higher QPS

---

**For presentation**: Show benchmark graphs, Restate UI (live journal), Temporal UI (event history). Discuss trade-offs: latency vs throughput, simplicity vs maturity.
