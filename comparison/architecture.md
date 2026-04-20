# Architecture: Hexagonal Design

## Ports & Adapters Pattern

```
┌───────────────────────────────────────────┐
│           LOS Service (Core)              │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │ Domain (common module)              │ │
│  │  • LoanApplication                  │ │
│  │  • ApplicationState (FSM)           │ │
│  │  • ApplicationEvent                 │ │
│  │  • LoanProductConfig                │ │
│  └─────────────────────────────────────┘ │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │ Application Layer                   │ │
│  │  • LoanApplicationService (FSM)     │ │
│  │  • ProductConfigLoader              │ │
│  │  • Port: WorkflowOrchestrator       │ │
│  └─────────────────────────────────────┘ │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │ API Layer                           │ │
│  │  • LoanController                   │ │
│  │  • UnderwritingController           │ │
│  │  • InternalController (events)      │ │
│  └─────────────────────────────────────┘ │
└───────────────┬───────────────────────────┘
                │ WorkflowOrchestrator port
         ┌──────┴──────┐
         │             │
┌────────▼──────┐  ┌───▼──────────────┐
│ Restate       │  │ Temporal         │
│ Adapter       │  │ Adapter          │
│               │  │                  │
│ • HTTP client │  │ • WorkflowClient │
│   to Restate  │  │ • Stub creation  │
└────────┬──────┘  └───┬──────────────┘
         │             │
┌────────▼─────────────▼──────┐
│  Workflow Implementations   │
│                             │
│  Restate:                   │
│  • CreditCheckWorkflow      │
│  • ExternalServiceClient    │
│  • LosClient                │
│                             │
│  Temporal:                  │
│  • CreditCheckWorkflow      │
│  • CreditCheckWorkflowImpl  │
│  • CreditCheckActivities    │
│  • CreditCheckActivitiesImpl│
└─────────────────────────────┘
```

## Key Architectural Decisions

### 1. Domain-Driven Design
- **Pure domain:** `common` module has zero dependencies on workflow engines
- **Sealed interfaces:** ApplicationState, ApplicationEvent for type safety
- **FSM in service:** LoanApplicationService enforces state transitions

### 2. Hexagonal (Ports & Adapters)
- **Port:** `WorkflowOrchestrator` interface in `los-service`
- **Adapters:** `RestateWorkflowOrchestrator`, `TemporalWorkflowOrchestrator`
- **Configuration:** `@ConditionalOnProperty` for adapter selection

### 3. Event Sourcing Lite
- Events appended via `/internal/applications/{id}/events`
- FSM validates transitions before applying events
- Workflows call LOS service to apply domain events

### 4. Configuration Over Code
- Product configs in YAML: `product-configs.yaml`
- Workflow structure in code, parameters in config
- Three products: personal_loan, auto_loan, mortgage

## Data Flow

### Submission
```
User → LoanController.submitApplication()
     → LoanApplicationService.submitApplication()
     → InMemoryApplicationStore.save()
     → WorkflowOrchestrator.startCreditCheck()
     → [Restate or Temporal]
```

### Workflow Execution
```
Restate/Temporal Workflow
  → ExternalServiceClient (httpbin-proxy)
  → LosClient.applyEvent()
  → InternalController.applyEvent()
  → LoanApplicationService.applyEvent()
  → FSM transition validation
  → InMemoryApplicationStore.save()
```

### Query
```
User → LoanController.getApplication(id)
     → LoanApplicationService.getApplication()
     → InMemoryApplicationStore.findById()
```

## Dependency Flow

```
┌──────────┐
│ common   │ (no dependencies)
└────┬─────┘
     │
┌────▼──────────┐
│ los-service   │ (Spring Boot, common)
└────┬──────────┘
     │ runtime dependency (not compile)
     ▼
┌────────────────┐
│ restate-impl   │ (Restate SDK, common)
└────────────────┘

┌────────────────┐
│ temporal-impl  │ (Temporal SDK, common)
└────────────────┘

┌────────────────┐
│ httpbin-proxy  │ (Spring Boot)
└────────────────┘
```

**Key insight:** `los-service` doesn't compile-depend on workflow implementations. They communicate via HTTP (Restate) or Temporal SDK (Temporal).

## Scalability Points

### Horizontal
- **LOS service:** Stateless (uses in-memory store, but production would use DB)
- **Restate service:** Stateless handler, state in Restate runtime
- **Temporal worker:** Poll-based, scale by adding workers
- **httpbin-proxy:** Stateless

### Vertical
- **Restate runtime:** Handles state, scales with memory
- **Temporal server:** History service sharded
- **Database:** Temporal requires PostgreSQL

## Failure Modes

### Service Restart
- **Restate:** Workflows resume from last journal entry
- **Temporal:** Workers reconnect, continue polling

### Network Partition
- **Restate:** HTTP call to LOS may fail, retry per ctx.run()
- **Temporal:** Activity timeout, auto-retry

### Data Loss
- **Restate:** Journal in Restate runtime (persistent volume)
- **Temporal:** Event history in PostgreSQL

---

**Design goal:** Swap workflow engines without changing domain or LOS service code. Achieved via hexagonal ports & adapters.
