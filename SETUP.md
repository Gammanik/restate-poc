# Restate Loan Application POC - Setup Guide

## Prerequisites

- Java 23 (configured in build.gradle.kts)
- Docker (for running Restate server)
- curl or Postman (for testing API)

## Architecture

```
┌─────────────┐         ┌──────────────────┐         ┌────────────────┐
│   Client    │ ──────> │ Spring Boot App  │ ──────> │ Restate Server │
│             │         │   (port 8081)    │         │  (port 8080)   │
└─────────────┘         └──────────────────┘         └────────────────┘
                               │                              │
                               │                              │
                               └──────────────────────────────┘
                                     Restate Services
                                    (port 9080 - endpoint)
```

## Step 1: Start Restate Server

Restate server acts as the runtime that orchestrates workflows and services.

```bash
# Start Restate server using Docker
docker run --name restate_dev --rm -it \
  --network=host \
  docker.io/restatedev/restate:latest
```

The Restate server will start on:
- **Admin API**: http://localhost:9070
- **Ingress API**: http://localhost:8080

## Step 2: Build the Application

```bash
./gradlew clean build
```

## Step 3: Start the Application

The application runs two components:
1. **Restate Endpoint** (port 9080) - Hosts the services
2. **Spring Boot API** (port 8081) - REST API for clients

```bash
./gradlew bootRun
```

You should see:
```
Starting Restate endpoint on port 9080...
Restate endpoint started successfully on port 9080
Starting Spring Boot application...
Started LoanApplicationKt in X.XXX seconds
```

## Step 4: Register Services with Restate

After the application starts, register the endpoint with Restate server:

```bash
# Register the endpoint
curl -X POST http://localhost:9070/endpoints \
  -H 'Content-Type: application/json' \
  -d '{"uri": "http://localhost:9080"}'
```

Expected response:
```json
{
  "id": "...",
  "services": [
    {"name": "CreditCheckService", "revision": 1},
    {"name": "DecisionService", "revision": 1},
    {"name": "ContractGenerationService", "revision": 1},
    {"name": "LoanApplicationWorkflow", "revision": 1}
  ]
}
```

**Verify registration:**

```bash
# List registered services
curl http://localhost:9070/services
```

## Step 5: Test the Application

### Submit a loan application:

```bash
curl -X POST http://localhost:8081/api/checkCredit \
  -H 'Content-Type: application/json' \
  -d '{
    "applicantName": "John Doe",
    "amount": 50000,
    "income": 100000
  }'
```

### Expected Response Examples:

**Approved Application** (high income ratio):
```json
{
  "applicationId": "abc12345",
  "decision": "APPROVED",
  "creditScore": 780,
  "message": "Congratulations! Your loan application has been approved. Contract: CONTRACT-abc12345-1234567890",
  "contractId": "CONTRACT-abc12345-1234567890"
}
```

**Rejected Application** (low income ratio):
```json
{
  "applicationId": "xyz98765",
  "decision": "REJECTED",
  "creditScore": 520,
  "message": "We're sorry, your loan application has been rejected based on credit assessment."
}
```

**Manual Review** (borderline ratio):
```json
{
  "applicationId": "def45678",
  "decision": "MANUAL_REVIEW",
  "creditScore": 640,
  "message": "Application pending manual review. Please contact our team."
}
```

## Decision Logic

The application uses income-to-loan ratio to calculate credit score:

| Income/Amount Ratio | Credit Score Range | Decision |
|---------------------|-------------------|----------|
| >= 5.0 | 750-850 | APPROVED |
| >= 3.0 | 650-750 | Likely APPROVED |
| >= 2.0 | 550-650 | MANUAL_REVIEW |
| >= 1.0 | 450-550 | REJECTED |
| < 1.0 | 300-450 | REJECTED |

**Decision Rules:**
- Score >= 750 → **APPROVED** (contract generated)
- 600 <= Score < 680 → **MANUAL_REVIEW**
- Otherwise → **REJECTED**

## Troubleshooting

### Error: "ClassNotFoundException: org.reactivestreams.Publisher"

This error occurs when Kotlin coroutines dependencies are missing. The project requires:
- `kotlinx-coroutines-core`
- `kotlinx-coroutines-reactor`

These are already included in `build.gradle.kts`. If you encounter this error:

```bash
./gradlew clean build
```

This will re-download all dependencies including reactive-streams.

### Error: "service '' not found"

This error occurs when services are not registered with Restate. Make sure to:

1. Restate server is running on port 8080
2. Application is running on port 9080 (endpoint)
3. Services are registered using the registration command (Step 4)

```bash
# Check if Restate server is running
curl http://localhost:9070/health

# Check registered services
curl http://localhost:9070/services

# Re-register if needed
curl -X POST http://localhost:9070/endpoints \
  -H 'Content-Type: application/json' \
  -d '{"uri": "http://localhost:9080"}'
```

### Error: "Connection refused to localhost:8080"

Restate server is not running. Start it with Docker:

```bash
docker run --name restate_dev --rm -it \
  --network=host \
  docker.io/restatedev/restate:latest
```

### Application won't start

Check if ports are available:

```bash
# Check port 8081 (Spring Boot)
lsof -i :8081

# Check port 9080 (Restate endpoint)
lsof -i :9080

# Check port 8080 (Restate server)
lsof -i :8080
```

## Monitoring

### Restate Admin UI

Access the Restate admin interface:

```
http://localhost:9070
```

### View Workflow Invocations

```bash
# List all invocations
curl http://localhost:9070/invocations

# Get specific workflow state
curl http://localhost:9070/invocations/<invocation-id>
```

### Application Logs

The application logs all workflow steps:

```
INFO  o.e.workflow.LoanApplicationWorkflow : Starting loan application workflow for: abc12345
INFO  o.e.workflow.LoanApplicationWorkflow : Running credit check for application: abc12345
INFO  o.e.service.CreditCheckService       : CreditCheckService: Attempt 1 for application abc12345
INFO  o.e.service.CreditCheckService       : CreditCheckService: Completed for application abc12345, score: 780
INFO  o.e.workflow.LoanApplicationWorkflow : Credit check completed for application: abc12345, score: 780
INFO  o.e.service.DecisionService          : DecisionService: Evaluating application abc12345 with credit score 780
INFO  o.e.service.DecisionService          : DecisionService: Application abc12345 decision: APPROVED
INFO  o.e.workflow.LoanApplicationWorkflow : Decision for application abc12345: APPROVED
INFO  o.e.service.ContractGenerationService: ContractGenerationService: Generating contract for application abc12345
INFO  o.e.workflow.LoanApplicationWorkflow : Generating contract for approved application: abc12345
```

## Restate Features Demonstrated

1. **Durable Execution** - Workflow state persists across failures
2. **Automatic Retries** - Credit check service simulates 30% failure rate on first attempt
3. **Virtual Objects** - Each loan application has its own isolated state
4. **Service Composition** - Workflow orchestrates multiple services
5. **Type Safety** - KSP generates type-safe clients

## Architecture Details

See [docs/README.md](docs/README.md) for diagrams and detailed architecture documentation.

## Development

### Rebuild after code changes

```bash
./gradlew clean build
# Then restart the application
./gradlew bootRun
```

### Re-register services

If you change service signatures, re-register with Restate:

```bash
curl -X POST http://localhost:9070/endpoints \
  -H 'Content-Type: application/json' \
  -d '{"uri": "http://localhost:9080", "force": true}'
```

## Stopping Services

```bash
# Stop application: Ctrl+C in terminal

# Stop Restate server
docker stop restate_dev
```
