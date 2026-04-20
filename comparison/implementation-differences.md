# Implementation Differences: Restate vs Temporal

## Core Philosophy

**Restate:** Durable functions with automatic journaling. Write normal code, Restate makes it durable.

**Temporal:** Workflow orchestration with strict replay semantics. Separate orchestration (workflow) from execution (activities).

## Code Structure

### Restate
```
CreditCheckWorkflow.java
├── @Workflow annotation
├── run(Context ctx, Request req) - main handler
└── underwriterDecision(Context ctx, Decision d) - signal handler

All logic in workflow, external calls via ctx.run()
```

### Temporal
```
CreditCheckWorkflow.java - interface
CreditCheckWorkflowImpl.java - orchestration logic
CreditCheckActivities.java - interface
CreditCheckActivitiesImpl.java - external calls

Separation of concerns enforced
```

## External Service Calls

### Restate
```java
ConsentRecord consent = ctx.run("capture-consent", ConsentRecord.class, () -> {
    return externalClient.captureConsent(appId, List.of("AECB", "OpenBanking"));
});
```
- Single step, inline
- Retry implicit (Restate auto-retries failed runs)
- Type-safe with generics

### Temporal
```java
// In workflow
ConsentRecord consent = activities.captureConsent(appId);

// In activity implementation
@Override
public ConsentRecord captureConsent(UUID appId) {
    // Actual HTTP call here
}
```
- Two-step: workflow calls activity stub, activity executes
- Retry configured via ActivityOptions
- Strong boundary between orchestration and execution

## State & Durability

### Restate
```java
ctx.run("fetch-aecb", AecbReport.class, () -> {
    return externalClient.fetchAecb(...);
});
// Result automatically persisted in journal
```
- Journal stores all ctx.run() results
- Replay from journal on failure
- No explicit state management needed

### Temporal
```java
AecbReport aecb = activities.fetchAecb(...);
// Stored in workflow event history
```
- Event history stores activity results
- Replay entire workflow code on resume
- Must be deterministic (no System.currentTimeMillis, no Random)

## Human-in-the-Loop

### Restate (Simplified in POC)
```java
var awakeable = ctx.awakeable(UnderwritingDecision.class);
ctx.set(DECISION_ID_KEY, awakeable.id());
UnderwritingDecision decision = awakeable.await();

// Resolve via separate handler
ctx.awakeableHandle(awakeableId).resolve(decision);
```
- Awakeables for external signals
- Store awakeable ID in workflow state
- Currently simplified in POC (marked as WIP)

### Temporal
```java
private UnderwritingDecision pendingDecision = null;

@SignalMethod
public void underwriterDecision(UnderwritingDecision decision) {
    this.pendingDecision = decision;
}

@WorkflowMethod
public void run(...) {
    Workflow.await(() -> pendingDecision != null);
}
```
- Signal methods to receive external input
- Workflow.await() blocks until condition met
- Workflow instance variable for state

## Retry Configuration

### Restate
```java
ctx.run("fetch-aecb", AecbReport.class,
    RetryPolicy.exponential(5, Duration.ofSeconds(2)),
    () -> externalClient.fetchAecb(...)
);
```
- Per-operation inline
- No global config needed
- Flexible, local control

### Temporal
```java
ActivityOptions options = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(30))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(5)
        .build())
    .build();

activities = Workflow.newActivityStub(CreditCheckActivities.class, options);
```
- Per-activity-stub global config
- Applied to all methods on stub
- Centralized, type-safe

## Timeouts & SLA

### Restate
```java
var awakeable = ctx.awakeable(UnderwritingDecision.class);
var timeout = ctx.timer(config.underwritingSla());
// Select/race between awakeable and timeout
```
- ctx.timer() for durable timers
- Race with Select (not shown in POC)
- Timer survives restarts

### Temporal
```java
Workflow.await(
    config.underwritingSla(),
    () -> pendingDecision != null
);
if (pendingDecision == null) {
    // Timeout
}
```
- Workflow.await() with timeout
- Built-in, simple
- Part of workflow replay

## Versioning

### Restate
- No built-in versioning in SDK 1.x/2.0
- Deploy new version, workflows continue
- Breaking changes require manual migration

### Temporal
```java
int version = Workflow.getVersion("add_open_banking", Workflow.DEFAULT_VERSION, 1);
if (version == 1) {
    // New behavior
} else {
    // Old behavior for replaying workflows
}
```
- getVersion() for safe rollouts
- Old workflows replay with old code path
- Allows breaking changes without migration

## Observability

### Restate
- Full journal visible in UI (http://localhost:9070)
- Shows all ctx.run() steps, inputs, outputs
- Single invocation trace

### Temporal
- Event history in UI (http://localhost:8088)
- Shows activity schedules, completions, signals
- Distributed trace across workers

## Deployment

### Restate
```bash
./gradlew :restate-impl:run  # Start service on port 9080
# Restate server at 8080, auto-discovers services
```
- Single binary per service
- Restate server separate (runtime + UI)
- Register via HTTP endpoint

### Temporal
```bash
docker compose up temporal temporal-ui postgres  # Temporal cluster
./gradlew :temporal-impl:run  # Start worker
```
- Worker processes poll task queues
- Temporal server cluster required
- Workers scale independently

## When to Use Which

**Use Restate if:**
- You want simple durable functions
- You prefer single binary deployment
- You have variable workflows (SaaS multi-tenant)
- You need lightweight setup

**Use Temporal if:**
- You need battle-tested enterprise workflows
- You have complex long-running processes
- You require strict versioning guarantees
- You're building mission-critical systems (finance, healthcare)

**Both handle:**
- Retries, timeouts, durability
- Workflow state persistence
- Human-in-the-loop patterns
- Distributed workflows at scale

---

**For MAL lending:** Both can work. Restate simpler ops, Temporal more mature ecosystem. Decision depends on team expertise and existing infrastructure.
