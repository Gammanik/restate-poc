# Performance Optimization Guide

## Running the Benchmark

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start httpbin-proxy
./gradlew :httpbin-proxy:bootRun &

# 3. Test Restate
./gradlew :restate-impl:run &
./gradlew :los-service:bootRun &

# Register with Restate
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# Benchmark
python3 benchmark.py --stress

# 4. Test Temporal (stop Restate first)
pkill -f 'restate-impl|los-service'
./gradlew :temporal-impl:run &
SERVER_PORT=8001 ./gradlew :los-service:bootRun &

# Benchmark
python3 benchmark.py --stress

# 5. Visualization
# Already generated in benchmark-graph-latest.png
```

## Interpreting Results

### Difference < 10%
**Parity** - both engines have similar performance.
- Choice depends on ecosystem and team experience
- Temporal: mature ecosystem, many integrations
- Restate: simpler operations, fewer moving parts

### Restate 15-30% faster
**Typical result** - architectural advantage of Restate:
- Fewer network hops (HTTP direct vs gRPC + polling)
- No worker polling overhead
- In-memory journal (before persistence)
- But: fewer optimizations for high load

**When to choose Restate:**
- Latency-sensitive operations (< 100ms requirement)
- Medium QPS (< 1000 rps)
- Simplicity over scale
- Fast prototyping needed

### Temporal 15-30% faster
**Rare result** - means Temporal is well-tuned:
- Worker pool more efficiently utilized
- Batching activity schedules works
- Connection pool configured correctly
- But: requires tuning

**When to choose Temporal:**
- High throughput (> 10k rps)
- Long-running workflows (days/weeks)
- Enterprise features needed (RBAC, multi-tenancy)
- Production-ready out of the box

### Difference > 30%
**Anomaly** - something is wrong:
- Check httpbin-proxy latency (should be ~2s for AECB)
- Check CPU/Memory (possible throttling)
- One engine may be incorrectly configured

## Optimizing Restate

### 1. Netty Threads
```bash
# Add to restate-impl/src/main/resources/application.properties
-Dio.netty.eventLoopThreads=16  # default = CPU cores * 2
```

### 2. HTTP/2
```java
// RestateApp.java
RestateHttpEndpointBuilder.builder()
    .bind(new CreditCheckWorkflow())
    .withHttp2()  // if Restate SDK supports
    .buildAndListen(9080);
```

### 3. Journal Compression (if available)
```
# Restate config
restate.journal.compression=true
```

## Optimizing Temporal

### 1. Worker Concurrency
```java
// TemporalApp.java
WorkerOptions options = WorkerOptions.newBuilder()
    .setMaxConcurrentActivityExecutionSize(50)  // default: 200
    .setMaxConcurrentWorkflowTaskExecutionSize(100)
    .build();

Worker worker = factory.newWorker(TASK_QUEUE, options);
```

### 2. Batching Activities
```java
// In CreditCheckWorkflowImpl
// Instead of sequential:
ConsentRecord consent = activities.consent(...);
AecbReport aecb = activities.aecb(...);

// Parallel (if possible):
Promise<ConsentRecord> consentPromise = Async.function(activities::consent, ...);
Promise<AecbReport> aecbPromise = Async.function(activities::aecb, ...);
ConsentRecord consent = consentPromise.get();
AecbReport aecb = aecbPromise.get();
```

### 3. Activity Options Tuning
```java
ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(10))  // shorter timeout
    .setScheduleToStartTimeout(Duration.ofSeconds(5))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(2)  // fewer retries
        .setBackoffCoefficient(1.5)
        .build())
    .build();
```

## General Optimizations

### 1. HTTP Connection Pooling
```java
// common/client/WorkflowClient.java
private final OkHttpClient http = new OkHttpClient.Builder()
    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
    .build();
```

### 2. Caching Product Configs
```java
// los-service/application/config/ProductConfigLoader.java
@Cacheable("product-configs")
public LoanProductConfig getConfig(String productId) { ... }
```

### 3. Async LOS Callbacks
```java
// Instead of synchronous:
client.notifyLos(appId, event);

// Asynchronous:
CompletableFuture.runAsync(() -> client.notifyLos(appId, event));
```

### 4. Reduce httpbin-proxy Latency
```yaml
# httpbin-proxy/src/main/resources/application.yaml
simulation:
  latency:
    aecb: 500  # instead of 2000
    open_banking: 200  # instead of 500
```

## Expected Optimization Results

| Optimization | Restate Gain | Temporal Gain |
|-------------|--------------|---------------|
| HTTP pooling | +10-15% | +10-15% |
| Worker concurrency | - | +20-30% |
| Async callbacks | +15-20% | +15-20% |
| Reduce httpbin latency | +50%+ | +50%+ |
| Config caching | +5% | +5% |

**Comprehensive optimization**: 2-3x improvement possible with proper tuning.

## Profiling

### JVM Profiling
```bash
# Add at startup
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=profile.jfr
```

### Analysis with JFR
```bash
jcmd <PID> JFR.start duration=60s filename=restate-profile.jfr
jcmd <PID> JFR.stop

# Open in JMC (Java Mission Control)
jmc restate-profile.jfr
```

## Key Takeaways

1. **Restate advantage**: latency due to simpler architecture
2. **Temporal advantage**: throughput and stability with proper tuning
3. **Hexagonal architecture works**: one WorkflowClient for both
4. **Real choice** depends on:
   - Latency requirements (< 50ms → Restate, < 500ms → either)
   - Throughput (< 1k rps → either, > 10k rps → Temporal)
   - Team experience (existing Temporal expertise → Temporal)
   - Operations (simplicity → Restate, maturity → Temporal)

**Next steps:**
- Add Grafana/Prometheus metrics
- Stress test: 10k+ rps sustained load
- Recovery test: kill during workflow execution
- Cost analysis: infrastructure per 1M workflows
