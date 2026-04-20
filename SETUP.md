# Setup Guide

## Prerequisites

- Java 21
- Docker & Docker Compose
- Gradle 8+ (or use `./gradlew`)

## Build

```bash
./gradlew build
```

## Run Locally

### Option 1: Restate Setup

```bash
# Terminal 1: Infrastructure
docker compose up -d restate

# Terminal 2: httpbin-proxy
./gradlew :httpbin-proxy:bootRun

# Terminal 3: Restate service
./gradlew :restate-impl:run

# Terminal 4: LOS service (Restate mode)
WORKFLOW_ENGINE=restate ./gradlew :los-service:bootRun

# Terminal 5: Register Restate service
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# Terminal 6: Test
./demo-script.sh happy-path
```

### Option 2: Temporal Setup

```bash
# Terminal 1: Infrastructure
docker compose up -d postgres temporal temporal-ui

# Terminal 2: httpbin-proxy
./gradlew :httpbin-proxy:bootRun

# Terminal 3: Temporal worker
./gradlew :temporal-impl:run

# Terminal 4: LOS service (Temporal mode)
WORKFLOW_ENGINE=temporal ./gradlew :los-service:bootRun

# Terminal 5: Test
./demo-script.sh happy-path
```

## Verify

### Restate
- UI: http://localhost:9070
- Admin API: `curl http://localhost:9070/services`

### Temporal
- UI: http://localhost:8088
- gRPC: localhost:7233

### LOS Service
- REST API: http://localhost:8000
- Health: `curl http://localhost:8000/api/applications/{id}`

### httpbin-proxy
- Status: `curl http://localhost:8090/config/status`

## Ports

| Service | Port | Protocol |
|---------|------|----------|
| Restate Ingress | 8080 | HTTP |
| Restate Admin | 9070 | HTTP |
| Restate Service | 9080 | HTTP |
| Temporal Server | 7233 | gRPC |
| Temporal UI | 8088 | HTTP |
| PostgreSQL | 5432 | TCP |
| LOS Service | 8000 | HTTP |
| httpbin-proxy | 8090 | HTTP |

## Configuration

### Switch Workflow Engine
Edit `los-service/src/main/resources/application.yaml` or set env var:
```bash
WORKFLOW_ENGINE=restate  # or temporal
```

### Product Configs
Edit `los-service/src/main/resources/product-configs.yaml` to change:
- Timeouts per stage
- Retry counts
- Auto-approve/reject thresholds
- Underwriting SLA

### Failure Simulation
```bash
# Increase AECB failure rate to 50%
curl -X PUT "http://localhost:8090/config/failure-rate/aecb?rate=0.5"
```

## Troubleshooting

### Restate service not discoverable
```bash
# Register manually
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'
```

### Temporal worker not connecting
Check Temporal server is up:
```bash
docker compose ps temporal
docker compose logs temporal
```

### LOS service returns 500
Check which orchestrator is active:
```bash
# Logs will show which adapter loaded
./gradlew :los-service:bootRun | grep "WorkflowOrchestrator"
```

## Clean Build

```bash
./gradlew clean build
docker compose down -v  # Remove volumes
```
