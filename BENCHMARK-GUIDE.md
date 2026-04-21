# Benchmark Guide

## Quick Start

```bash
# 1. Start services
docker compose up -d
./gradlew :httpbin-proxy:bootRun &
./gradlew :restate-impl:run &
./gradlew :los-service:bootRun &

# 2. Register Restate
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# 3. Run benchmark
python3 benchmark.py
```

## Benchmark Modes

### Quick Test (20 requests)
```bash
python3 benchmark.py
```
- Fast validation
- Results: `benchmark-results.csv`
- ASCII charts in terminal

### Custom Load Test
```bash
python3 benchmark.py --requests 500
```
- Specify any number of requests
- Good for specific load testing

### Stress Test (100-15k requests)
```bash
# Install matplotlib for graphs
pip3 install matplotlib

# Run stress test
python3 benchmark.py --stress
```

**What it does**:
- Tests: 100, 500, 1000, 2500, 5000, 10000, 15000 requests
- Generates 4 graphs:
  1. Throughput vs Load
  2. Average Latency vs Load
  3. p95 Latency vs Load
  4. Success Rate vs Load
- Results: `benchmark-stress-results.csv`
- Graphs: `benchmark-graph-latest.png`

## Comparing Restate vs Temporal

### Test Restate
```bash
# Already running from Quick Start above
python3 benchmark.py --stress
```

### Switch to Temporal
```bash
# Stop Restate components
pkill -f 'restate-impl|los-service'

# Start Temporal
./gradlew :temporal-impl:run &
SERVER_PORT=8001 ./gradlew :los-service:bootRun &

# Wait 10 seconds for startup
sleep 10

# Run benchmark
python3 benchmark.py --stress
```

## Interpreting Results

### Terminal Output
```
🏆 Average latency (ms):
  restate      🏆 ███████████████████████  2847.00  -19.2%
  temporal        ████████████████████████ 3523.00  +23.7%
```

- **Winner** 🏆: Lower is better for latency
- **Percentage**: Difference vs competitor
- **Negative %**: Faster (better)
- **Positive %**: Slower (worse)

### Analysis Section
```
🚀 WINNER: Restate is 19.2% faster
   → Fewer network hops (HTTP direct vs gRPC)
   → No worker polling overhead
   → Recommendation: for low-latency operations (< 100ms)
```

### Graphs (with matplotlib)
- **Throughput vs Load**: Higher is better
- **Latency vs Load**: Lower is better
- **Success Rate**: Should stay at 100%

## Metrics Explained

- **avg_ms**: Average response time
- **p50_ms**: Median (50% of requests faster)
- **p95_ms**: 95th percentile (95% of requests faster)
- **p99_ms**: 99th percentile (99% of requests faster)
- **req_per_sec**: Throughput (requests per second)
- **success_rate**: % of successful requests

## Optimization Tips

See [OPTIMIZATION.md](OPTIMIZATION.md) for detailed guide:
- HTTP connection pooling
- Worker thread tuning
- Async callbacks
- Reduce httpbin-proxy latency
- Netty/gRPC optimization

## Troubleshooting

### matplotlib not installed
```bash
pip3 install matplotlib
```

### Services not running
```bash
# Check if services are up
curl http://localhost:8000
curl http://localhost:8001

# Check logs
docker compose logs
tail -f httpbin-proxy.log
```

### Restate not registered
```bash
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# Verify registration
curl http://localhost:9070/deployments
```

### Low success rate (< 100%)
- Increase timeout: Edit `timeout=30` in benchmark.py
- Check service logs for errors
- Reduce parallel workers: Edit `max_workers` in benchmark.py

## Example Results

From actual run (20 requests each):
```
Engine      Avg (ms)  p95 (ms)  RPS    Winner
restate     2847      3089      7.02   🏆
temporal    3523      3890      5.67
```

**Conclusion**: Restate 19% faster due to simpler architecture.
