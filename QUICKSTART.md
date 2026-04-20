# Quick Start Guide

Get the Restate Loan Application POC running in 5 minutes.

## Prerequisites

- Docker (for Restate server)
- Java 23
- curl and jq (for testing)

## Step-by-Step

### 1. Start Restate Server (Terminal 1)

```bash
docker run --name restate_dev --rm -it \
  --network=host \
  docker.io/restatedev/restate:latest
```

**Wait for:** "Restate server started successfully"

### 2. Build and Run Application (Terminal 2)

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun
```

**Wait for:** "Started LoanApplicationKt"

### 3. Register Services (Terminal 3)

```bash
curl -X POST http://localhost:9070/endpoints \
  -H 'Content-Type: application/json' \
  -d '{"uri": "http://localhost:9080"}'
```

**Expected output:**
```json
{
  "id": "...",
  "services": [
    {"name": "CreditCheckService", ...},
    {"name": "DecisionService", ...},
    {"name": "ContractGenerationService", ...},
    {"name": "LoanApplicationWorkflow", ...}
  ]
}
```

### 4. Test the API

#### Option A: Use the test script

```bash
./test-api.sh
```

This will run 4 test scenarios automatically.

#### Option B: Manual test

```bash
curl -X POST http://localhost:8081/api/checkCredit \
  -H 'Content-Type: application/json' \
  -d '{
    "applicantName": "John Doe",
    "amount": 50000,
    "income": 100000
  }' | jq '.'
```

**Expected response:**
```json
{
  "applicationId": "abc12345",
  "decision": "APPROVED",
  "creditScore": 780,
  "message": "Congratulations! Your loan application has been approved. Contract: CONTRACT-abc12345-...",
  "contractId": "CONTRACT-abc12345-..."
}
```

## What Just Happened?

1. **Restate Server** (port 8080/9070) - Orchestrates durable workflows
2. **Application Services** (port 9080) - Hosts the business logic
3. **Spring Boot API** (port 8081) - Client-facing REST API
4. **Workflow Execution** - Restate coordinated the entire loan process:
   - Credit check with automatic retry
   - Decision making based on score
   - Contract generation for approved loans

## View Workflow State

Check Restate admin API:

```bash
# List all services
curl http://localhost:9070/services | jq '.'

# List all invocations
curl http://localhost:9070/invocations | jq '.'

# Get specific invocation details
curl http://localhost:9070/invocations/<invocation-id> | jq '.'
```

## Troubleshooting

### "Connection refused" to localhost:8080
→ Restate server is not running. Start it (Step 1)

### "service '' not found"
→ Services not registered. Register them (Step 3)

### "ClassNotFoundException: org.reactivestreams.Publisher"
→ Dependencies not downloaded. Run: `./gradlew clean build`

### Port already in use
```bash
# Check what's using the port
lsof -i :8080  # Restate server
lsof -i :8081  # Spring Boot
lsof -i :9080  # Services endpoint
```

## Next Steps

- 📖 Read [SETUP.md](SETUP.md) for detailed configuration
- 📊 Check [docs/README.md](docs/README.md) for workflow diagrams
- 🔍 Explore the source code in `src/main/kotlin/org/example/`

## Test Scenarios

The application uses **income-to-loan ratio** to determine outcomes:

| Scenario | Amount | Income | Ratio | Expected Decision |
|----------|--------|--------|-------|-------------------|
| High quality | $50k | $400k | 8.0 | APPROVED |
| Good quality | $50k | $250k | 5.0 | APPROVED |
| Borderline | $75k | $160k | 2.1 | MANUAL_REVIEW |
| Low quality | $100k | $30k | 0.3 | REJECTED |

**Decision Rules:**
- Credit Score ≥ 750 → **APPROVED**
- 600 ≤ Score < 680 → **MANUAL_REVIEW**
- Otherwise → **REJECTED**

## Stopping Services

1. Stop application: `Ctrl+C` in Terminal 2
2. Stop Restate server: `Ctrl+C` in Terminal 1 (or `docker stop restate_dev`)

## Architecture Diagram

```
┌─────────┐
│ Client  │
└────┬────┘
     │ HTTP POST /api/checkCredit
     ▼
┌─────────────────────┐
│  Spring Boot API    │
│  (port 8081)        │
└─────────┬───────────┘
          │ Restate Client Call
          ▼
┌─────────────────────┐         ┌──────────────────────┐
│  Restate Server     │◄────────│ Services Endpoint    │
│  (port 8080/9070)   │         │ (port 9080)          │
└─────────────────────┘         │                      │
                                │ - CreditCheckService │
                                │ - DecisionService    │
                                │ - ContractService    │
                                │ - LoanWorkflow       │
                                └──────────────────────┘
```

## Key Features Demonstrated

✅ **Durable Execution** - Workflow survives crashes
✅ **Automatic Retries** - 30% simulated failure rate
✅ **Service Orchestration** - Multi-step workflow
✅ **Type Safety** - Kotlin + KSP code generation
✅ **State Persistence** - Via Restate event sourcing

---

**Need help?** See [SETUP.md](SETUP.md) for detailed troubleshooting.
