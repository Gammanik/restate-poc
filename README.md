# Restate vs Temporal POC

Side-by-side comparison of Restate and Temporal implementing the same UAE credit origination workflow.

## What This Shows

**Same 7-stage workflow, two engines:** AECB bureau check, Open Banking, consent capture, decisioning, and manual underwriting — all executed identically via hexagonal architecture with swappable adapters.

## Architecture

```
┌─────────────┐
│ LOS Service │ ← Hexagonal core (domain + ports)
└──────┬──────┘
       │ WorkflowOrchestrator port
    ┌──┴──┐
    │     │
┌───▼──┐ ┌▼────────┐
│Restate│ │Temporal│ ← Adapters (swappable)
└───┬───┘ └─────┬──┘
    │           │
┌───▼───────────▼─────┐
│  httpbin-proxy      │ ← Simulates AECB, Open Banking, Decision Engine
└─────────────────────┘
```

## Quick Start

```bash
# 1. Infrastructure
docker compose up -d

# 2. Services (in separate terminals)
./gradlew :httpbin-proxy:bootRun
./gradlew :restate-impl:run
WORKFLOW_ENGINE=restate ./gradlew :los-service:bootRun

# 3. Demo
./demo-script.sh happy-path
```

**UIs:**
- Restate: http://localhost:9070
- Temporal: http://localhost:8088

## Product Configurations

Three loan products with different workflows:

| Product | AECB | Open Banking | Auto-Approve Score | SLA Days |
|---------|------|--------------|-------------------|----------|
| Personal Loan | ✓ | ✓ | 700 | 7 |
| Auto Loan | ✓ | ✗ | 650 | 5 |
| Mortgage | ✓ | ✓ | 750 | 14 |

Config: `los-service/src/main/resources/product-configs.yaml`

## Comparison

| Aspect | Restate | Temporal |
|--------|---------|----------|
| Deployment | Single binary (port 9080) | Server cluster + workers |
| State visibility | Full journal in UI | Event history in UI |
| Retry | Per ctx.run(), inline config | Per activity, ActivityOptions |
| Human-in-loop | Awakeables (WIP) | Signals + Workflow.await |
| Determinism | No constraints | Strict (Workflow.currentTimeMillis) |
| Versioning | None (v1.x) | getVersion() for safe rollouts |

**Key difference:** Restate treats workflows as durable functions with automatic journaling. Temporal separates workflow (orchestration) from activities (side effects) with strict replay semantics.

## Project Structure

```
├── common/                  # Domain + DTOs (pure Java)
├── los-service/             # Spring Boot gateway + FSM
│   ├── application/port/    # WorkflowOrchestrator interface
│   └── adapter/             # Restate + Temporal adapters
├── restate-impl/            # Restate workflow
├── temporal-impl/           # Temporal workflow + activities
└── httpbin-proxy/           # External service simulator
```

## Key Files

- **Domain FSM:** `common/src/main/java/com/mal/lospoc/common/domain/ApplicationState.java`
- **Workflow (Restate):** `restate-impl/src/main/java/com/mal/lospoc/restate/workflow/CreditCheckWorkflow.java`
- **Workflow (Temporal):** `temporal-impl/src/main/java/com/mal/lospoc/temporal/workflow/CreditCheckWorkflowImpl.java`
- **Adapter Switching:** `los-service/src/main/resources/application.yaml` (`WORKFLOW_ENGINE` env var)

## Stack

Java 21, Spring Boot 3.4, Restate SDK 2.0, Temporal SDK 1.26, Docker Compose

## UAE Context

- **AECB:** Al Etihad Credit Bureau (national credit scoring)
- **Open Banking:** UAE Central Bank Open Finance framework
- **PDPL:** Personal Data Protection Law (consent requirement)

---

Built for engineering debrief: Ashish (EM) & Vitaliy (Senior Eng). Focus on architecture patterns, not production readiness.
