# Restate Loan Application POC

A proof-of-concept loan application workflow built with Restate and Kotlin/Spring Boot.

## Features

- **Durable workflow execution** with Restate's event sourcing
- **Automated credit checking** with retry policies
- **Decision-making** based on credit score thresholds
- **Manual review workflow** with timeout handling via durable promises
- **Contract generation** for approved loans
- **Type-safe service orchestration** with Kotlin coroutines

## What is Restate?

Restate is a modern workflow orchestration platform that combines the best of:
- **Durable execution** - Workflows survive crashes and restarts
- **Event sourcing** - Complete audit trail of all workflow events
- **RPC-style programming** - Write workflows as regular async code
- **Service-to-service communication** - Direct invocation patterns

### Key Differences from Temporal and Camunda

| Feature | Restate | Temporal | Camunda |
|---------|---------|----------|---------|
| **Programming Model** | Async/await with coroutines | Activities + Workflows | BPMN + Workers |
| **State Management** | Event sourcing + snapshots | Event history | Zeebe state |
| **Service Calls** | Direct RPC-style | Activity stubs | Job workers |
| **Durable Promises** | Built-in primitives | Signals/Queries | Message correlation |
| **Infrastructure** | Single server | Server cluster | Zeebe + Elasticsearch |

## Quick Start

1. Start Restate server:
```bash
docker-compose up -d
```

2. Build the application:
```bash
./gradlew build
```

3. Run the application:
```bash
./gradlew bootRun
```

4. Register services with Restate:
```bash
curl -X POST http://localhost:8080/deployments \
  -H "Content-Type: application/json" \
  -d '{"uri": "http://host.docker.internal:9080"}'
```

## Usage

Submit a loan application:
```bash
curl -X POST http://localhost:8080/api/checkCredit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName": "John Doe",
    "amount": 50000,
    "income": 75000
  }'
```

## Workflow

1. **Credit Check** - Validates applicant creditworthiness (with automatic retries)
2. **Decision** - Auto-approve, reject, or flag for manual review
   - Score ≥ 750: APPROVED
   - Score 600-679: MANUAL_REVIEW
   - Score < 600: REJECTED
3. **Manual Review** (if needed) - 7-day timeout before auto-rejection
4. **Contract Generation** - Creates contract for approved loans

## Architecture

### Restate Workflow Pattern

```
LoanApplicationWorkflow (@Workflow)
    ├─> CreditCheckService (@Service)
    ├─> DecisionService (@Service)
    ├─> ContractGenerationService (@Service)
    └─> Durable Promises (for manual review)
```

### How Restate Differs

**Temporal Pattern:**
```kotlin
// Temporal uses activity stubs
val activity = Workflow.newActivityStub(CreditCheckActivity::class.java)
val result = activity.checkCredit(application)
```

**Restate Pattern:**
```kotlin
// Restate uses direct RPC-style calls
val result = ctx.runBlock {
    val client = CreditCheckServiceClient.fromContext(ctx)
    client.checkCredit(application)
}
```

**Key Benefits:**
- ✅ More natural async/await programming
- ✅ Simpler service-to-service communication
- ✅ Built-in durable promises for coordination
- ✅ Lighter infrastructure requirements

## Durable Promises for Manual Review

One of Restate's unique features is **durable promises** - a powerful primitive for workflow coordination:

```kotlin
// Create a durable promise that survives restarts
val reviewPromiseKey = DurablePromiseKey.of<Boolean>(
    "manual-review-${application.applicationId}-$attempt"
)

// Wait for the promise to be resolved (with timeout)
val approved = ctx.promise(reviewPromiseKey)
    .awaitable()
    .await()
```

To approve a manual review:
```bash
curl -X POST http://localhost:9070/LoanApplicationWorkflow/${APPLICATION_ID}/approveManualReview \
  -H "Content-Type: application/json" \
  -d '{"attempt": 1}'
```

## Tech Stack

- Kotlin 2.2.20
- Spring Boot 3.4.1
- Restate SDK 1.0.1
- Kotlin Coroutines

## Project Structure

```
restate-loan-poc/
├── src/main/kotlin/org/example/
│   ├── model/
│   │   └── LoanModels.kt          # Data models
│   ├── service/
│   │   ├── CreditCheckService.kt   # Credit check service
│   │   ├── DecisionService.kt      # Decision logic
│   │   └── ContractGenerationService.kt
│   ├── workflow/
│   │   └── LoanApplicationWorkflow.kt  # Main workflow
│   ├── controller/
│   │   └── LoanController.kt       # REST API
│   └── LoanApplication.kt          # Main application
├── build.gradle.kts
└── docker-compose.yml
```

## Why Choose Restate?

**Choose Restate when:**
- ✅ You want simpler infrastructure than Temporal
- ✅ You prefer RPC-style service calls over activities
- ✅ You need durable promises for coordination
- ✅ You want event sourcing with minimal overhead
- ✅ You're building microservices orchestration

**Consider Temporal when:**
- ❌ You need battle-tested, mature ecosystem
- ❌ You require extensive polyglot support
- ❌ You need advanced workflow versioning

**Consider Camunda when:**
- ❌ You need BPMN for business stakeholders
- ❌ You require built-in user task management
- ❌ Visual process modeling is essential

## Monitoring

Access Restate UI: `http://localhost:8080` (Admin API)

View registered services:
```bash
curl http://localhost:8080/services
```

View workflow invocations:
```bash
curl http://localhost:8080/invocations
```

## Learn More

- **Restate Docs**: https://docs.restate.dev/
- **Restate GitHub**: https://github.com/restatedev/restate
- **Comparison**: See main [README.md](../README.md) for Temporal vs Camunda vs Restate
