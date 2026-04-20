# Setup Guide

## Prerequisites

- Java 23
- Docker
- curl (for testing)

## Start Services

### Option 1: Docker Compose (Recommended)

```bash
# Start Restate server
docker-compose up -d

# Check status
docker-compose ps
```

### Option 2: Manual Docker

```bash
docker run --name restate_dev --rm -d \
  -p 8080:8080 -p 9070:9070 \
  --add-host=host.docker.internal:host-gateway \
  restatedev/restate:latest
```

## Build and Run Application

```bash
# Build
./gradlew build

# Run
./gradlew bootRun
```

Wait for: `Started LoanApplicationKt in X.XXX seconds`

## Register Services

```bash
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'
```

Expected output:
```json
{
  "id": "dp_...",
  "services": [
    {"name": "CreditCheckService", ...},
    {"name": "DecisionService", ...},
    {"name": "ContractGenerationService", ...},
    {"name": "LoanApplicationWorkflow", ...}
  ]
}
```

Verify:
```bash
curl http://localhost:9070/services | jq '.services[] | .name'
```

## Test

```bash
curl -X POST http://localhost:8081/api/checkCredit \
  -H 'Content-Type: application/json' \
  -d '{
    "applicantName": "John Doe",
    "amount": 50000,
    "income": 100000
  }'
```

## Troubleshooting

**Port already in use:**
```bash
# Kill existing process
lsof -i :9080 | grep LISTEN
kill <PID>

# Or restart from scratch
docker-compose down -v
docker-compose up -d
pkill -f bootRun
./gradlew bootRun
```

**Service not found:**
```bash
# Check services are registered
curl http://localhost:9070/services

# Re-register if needed
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'
```

**Restate not responding:**
```bash
# Check Docker container
docker ps | grep restate
docker logs restate-server

# Restart
docker-compose restart
```

## Stopping

```bash
# Stop application
Ctrl+C

# Stop Restate
docker-compose down

# Clean volumes (removes all data)
docker-compose down -v
```

## Monitoring

- **Web UI**: http://localhost:9070/ui/
- **Health**: `curl http://localhost:9070/health`
- **Services**: `curl http://localhost:9070/services`
- **Invocations**: `curl http://localhost:9070/invocations`

## Ports

- `8080` - Restate Ingress API
- `9070` - Restate Admin API + Web UI
- `9080` - Application Services Endpoint
- `8081` - Spring Boot REST API
