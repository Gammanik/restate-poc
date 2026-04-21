# Quick Start Guide

## ✅ Prerequisites

- Java 21
- Docker Desktop (running)
- Python 3 (for benchmark)
- Optional: `pip3 install matplotlib` (for graphs)

## 🚀 Launch Services

```bash
# 1. Start infrastructure (Temporal, Postgres)
docker compose up -d

# 2. Verify Docker services
docker compose ps
# Should show: temporal, postgres, restate, temporal-ui

# 3. Start application services
./gradlew :httpbin-proxy:bootRun &   # Port 8091 - Mock external services
./gradlew :restate-impl:run &        # Port 8000 - Restate workflow
./gradlew :temporal-impl:run &       # Port 8001 - Temporal workflow

# Wait 15-20 seconds for services to start
sleep 20

# 4. Verify services are up
curl http://localhost:8000  # Should return: {"status":"ok","service":"restate"}
curl http://localhost:8001  # Should return: {"status":"ok","service":"temporal"}
curl http://localhost:8091  # Should return 404 (but server is up)
```

## 📊 Run Benchmark

### Quick Test (30s at 100 RPS)
```bash
python3 benchmark.py
```

### Custom RPS Level
```bash
python3 benchmark.py --rps 500 --duration 30
```

### Full Stress Test (10-15k RPS)
```bash
# Install matplotlib for graphs
pip3 install matplotlib

# Run stress test (takes ~20 minutes)
python3 benchmark.py --stress

# Shorter stress test (10s per level instead of 30s)
python3 benchmark.py --stress --duration 10 --warmup-duration 5
```

### Results
- **CSV**: `benchmark-results.csv` or `benchmark-stress-results.csv`
- **Graphs**: `benchmark-graph-latest.png` (if matplotlib installed)
- **Terminal**: Real-time comparison table

## 📈 Expected Results

### Restate
- **Latency**: Lower (simpler architecture, fewer hops)
- **Throughput**: Similar to Temporal at low RPS, may drop at very high load
- **Best for**: Low-latency requirements (< 100ms), simple workflows

### Temporal
- **Latency**: Slightly higher (gRPC overhead, worker polling)
- **Throughput**: Better at high RPS (optimized worker pool)
- **Best for**: High-throughput (> 10k RPS), complex workflows, production maturity

## 🔧 Troubleshooting

### Docker not running
```bash
# Start Docker Desktop first, then:
docker compose up -d
```

### Services not starting
```bash
# Check logs
tail -f httpbin-proxy.log
tail -f restate.log
tail -f temporal.log

# Rebuild if needed
./gradlew clean build
```

### Port already in use
```bash
# Kill existing processes
pkill -f "httpbin-proxy|restate-impl|temporal-impl"

# Or change ports in docker-compose.yml and app configs
```

### Benchmark shows 0 RPS
```bash
# Verify services are responding
curl -X POST http://localhost:8000/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"productId":"personal_loan","userDetails":{"emiratesId":"784-1234-5678901-0","name":"Test","dateOfBirth":"1990-01-01","address":"Dubai","incomeClaimed":15000},"loanAmount":50000}'

# Should return: {"applicationId":"<uuid>","status":"submitted"}
```

## 🎯 What Gets Tested

Each workflow execution:
1. **Consent Capture** (~100ms simulated)
2. **AECB Credit Check** (~2000ms simulated)
3. **Open Banking** (~500ms simulated)
4. **Decisioning** (~200ms simulated)

Total simulated external call time: ~2.8s per application

Benchmark measures:
- **Throughput**: Actual RPS achieved
- **Latency**: p50, p95, p99 percentiles
- **Error Rate**: % of failed requests
- **Scalability**: Performance across different load levels

## 📁 Architecture

```
┌─────────────────┐       ┌─────────────────┐
│   Restate       │       │   Temporal      │
│   Port 8000     │       │   Port 8001     │
└────────┬────────┘       └────────┬────────┘
         │                         │
         └──────────┬──────────────┘
                    ↓
         ┌──────────────────┐
         │  httpbin-proxy   │  Simulates:
         │   Port 8091      │  - AECB
         └──────────────────┘  - Open Banking
```

## 🧪 Test Manually

```bash
# Submit application to Restate
curl -X POST http://localhost:8000/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": "personal_loan",
    "userDetails": {
      "emiratesId": "784-1234-5678901-0",
      "name": "Ahmed Test",
      "dateOfBirth": "1990-01-01",
      "address": "Dubai, UAE",
      "incomeClaimed": 15000
    },
    "loanAmount": 50000
  }'

# Submit to Temporal
curl -X POST http://localhost:8001/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": "personal_loan",
    "userDetails": {
      "emiratesId": "784-1234-5678901-0",
      "name": "Fatima Test",
      "dateOfBirth": "1990-01-01",
      "address": "Dubai, UAE",
      "incomeClaimed": 15000
    },
    "loanAmount": 50000
  }'
```

## 🎨 UI

- **Temporal UI**: http://localhost:8088 (view workflow history)
- **Restate UI**: http://localhost:9070 (view invocations)

## 🛑 Stop Everything

```bash
# Stop services
pkill -f "httpbin-proxy|restate-impl|temporal-impl"

# Stop Docker
docker compose down
```

## 📖 Documentation

- **Optimization Guide**: [OPTIMIZATION.md](OPTIMIZATION.md)
- **Benchmark Guide**: [BENCHMARK-GUIDE.md](BENCHMARK-GUIDE.md)
- **Full README**: [README.md](README.md)
