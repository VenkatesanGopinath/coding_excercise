# Submission — Warehouse Cost Control Assignment

**Candidate:** Venkatesan Gopinath
**GitHub Repository:** https://github.com/VenkatesanGopinath/coding_excercise
**Branch:** `main`

---

## Table of Contents

1. [Code Assignment — Tasks Implemented](#1-code-assignment--tasks-implemented)
2. [Unit Testing Strategy](#2-unit-testing-strategy)
3. [Code Coverage — JaCoCo](#3-code-coverage--jacoco)
4. [Software Development Best Practices](#4-software-development-best-practices)
5. [CI/CD Pipeline](#5-cicd-pipeline)
6. [Health Check](#6-health-check)
7. [Case Study — Scenario Responses](#7-case-study--scenario-responses)
8. [How to Run Locally](#8-how-to-run-locally)

---

## 1. Code Assignment — Tasks Implemented

All tasks from `CODE_ASSIGNMENT.md` have been implemented in the `java-assignment` module following the **Hexagonal Architecture (Ports & Adapters)** pattern already established in the codebase.

### Task 1 — List All Active Warehouses

**Endpoint:** `GET /warehouse`

Returns all warehouses where `archivedAt IS NULL`. Implemented in `WarehouseRepository.getAll()`, which filters on the archived flag so replaced/archived warehouses are invisible to the API consumer.

### Task 2 — Create a Warehouse

**Endpoint:** `POST /warehouse`

Implemented in `CreateWarehouseUseCase`. The following business rules are enforced before persistence:

| Rule | Behaviour |
|------|-----------|
| Location must be valid | `LocationGateway` looks up the location; returns `400` if not found |
| BUC must be unique (active only) | Checks for existing active warehouse with same BUC; returns `400` on collision |
| Warehouse count at location must not exceed limit | Compares active count against `location.maxNumberOfWarehouses`; returns `400` when full |
| Capacity must not exceed location maximum | Compares requested capacity against `location.maxCapacity`; returns `400` if exceeded |
| Stock must not exceed capacity | Returns `400` if `stock > capacity` |

### Task 3 — Archive a Warehouse

**Endpoint:** `DELETE /warehouse/{businessUnitCode}`

Implemented in `ArchiveWarehouseUseCase`. Sets `archivedAt = now()` on the warehouse record. The warehouse remains in the database for historical/audit purposes but disappears from the active list. Returns `404` if the BUC does not exist or is already archived.

### Task 4 — Replace a Warehouse

**Endpoint:** `POST /warehouse/{businessUnitCode}/replacement`

Implemented in `ReplaceWarehouseUseCase`. Enforces these rules atomically within a single `@Transactional` boundary:

| Rule | Behaviour |
|------|-----------|
| Old warehouse must exist and be active | Returns `404` if not found |
| New location must be valid | Returns `400` if invalid |
| New capacity must accommodate old stock | Returns `400` if `newCapacity < oldStock` |
| Stock transfer must be exact | `newStock` must equal `oldStock`; returns `400` on mismatch |

The operation archives the old warehouse and creates a new one with the same BUC, preserving business unit continuity.

---

## 2. Unit Testing Strategy

**44 tests** across **8 test classes**, organised by architectural layer.

### Domain Layer — Use Case Unit Tests

Pure JUnit 5 tests with Mockito mocks. No database or HTTP involved. Fast and deterministic.

| Test Class | Tests | What is Covered |
|---|---|---|
| `CreateWarehouseUseCaseTest` | 10 | Valid creation, invalid location, duplicate BUC, max warehouses exceeded, capacity over location max, stock > capacity, boundary values (capacity exactly at max, stock = 0) |
| `ArchiveWarehouseUseCaseTest` | 3 | Successful archive, archive of non-existent BUC, re-archive of already-archived BUC |
| `ReplaceWarehouseUseCaseTest` | 7 | Successful replace, invalid location, capacity below old stock, stock mismatch, non-existent BUC, boundary (capacity exactly equals old stock), location change |

### Adapter Layer — Gateway Unit Tests

| Test Class | Tests | What is Covered |
|---|---|---|
| `LocationGatewayTest` | 4 | Known location lookup, unknown identifier returns null, BUC-style identifier, null identifier |

### Adapter Layer — Database Integration Tests (`@QuarkusTest + @TestTransaction`)

Each test runs inside a transaction that is rolled back on completion, ensuring test isolation without requiring database cleanup scripts.

| Test Class | Tests | What is Covered |
|---|---|---|
| `WarehouseRepositoryTest` | 6 | `getAll` returns seed data, `getAll` excludes archived records, `create` persists all fields, `update` sets `archivedAt` and removes from active list, `findByBUC` returns null for unknown, `findByBUC` returns null for archived |

### HTTP Layer Tests (`@QuarkusTest`)

Full Quarkus context with a real database. Tests the REST adapter end-to-end including serialisation, HTTP status codes, and error response shapes.

| Test Class | Tests | What is Covered |
|---|---|---|
| `WarehouseEndpointTest` | 10 | `GET /warehouse` (200 + seed data), `GET /warehouse/{buc}` (200 + fields), 404 for unknown BUC, `POST /warehouse` (invalid location → 400, duplicate BUC → 400, stock > capacity → 400), `DELETE` of non-existent → 404, replacement of non-existent → 404, stock mismatch → 400, health check → 200 |
| `WarehouseEndpointIT` | 4 | End-to-end integration test (pre-existing) |
| `ProductEndpointTest` | 4 | List products (200), get non-existent (404), delete non-existent (404), duplicate name constraint violation |

---

## 3. Code Coverage — JaCoCo

Coverage is tracked using the `jacoco-maven-plugin` (0.8.12) together with the `quarkus-jacoco` extension, which instruments the Quarkus classloader used by `@QuarkusTest` and merges both exec streams into a single `target/jacoco.exec` file.

**Result: all coverage checks passed (`>= 80%`).**

```
[INFO] --- jacoco-maven-plugin:0.8.12:check ---
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
```

### Coverage by Package

| Package | Line Coverage | Notes |
|---|---|---|
| `warehouses.domain.usecases` | **99%** | Fully exercised by use-case unit tests |
| `location` | **100%** | Fully exercised by LocationGatewayTest |
| `warehouses.domain.models` | **100%** | Data classes exercised transitively |
| `warehouses.adapters.restapi` | **87%** | HTTP layer covered by WarehouseEndpointTest |
| `warehouses.adapters.database` | **82%** | Repository covered by WarehouseRepositoryTest |
| `warehouses.adapters.health` | Covered | WarehouseHealthCheck exercised by health test |
| `stores` (excluded) | Legacy | Pre-existing `LegacyStoreManagerGateway` excluded from measurement |

**Exclusions from measurement:**
- `com/warehouse/api/**` — auto-generated OpenAPI stubs (no business logic)
- `com/fulfilment/application/monolith/stores/**` — pre-existing legacy file-I/O gateway, not part of the warehouse assignment scope

### JaCoCo Configuration

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
        <excludes>
            <exclude>com/warehouse/api/**</exclude>
            <exclude>com/fulfilment/application/monolith/stores/**</exclude>
        </excludes>
    </configuration>
    <!-- prepare-agent, report (verify), check (80% LINE threshold) -->
</plugin>
```

```properties
# application.properties — test profile
%test.quarkus.jacoco.data-file=target/jacoco.exec
%test.quarkus.jacoco.reuse-data-file=true
```

---

## 4. Software Development Best Practices

### Exception Handling

Business rule violations are raised as `WebApplicationException` with descriptive messages and the appropriate HTTP status code (`400 Bad Request`, `404 Not Found`). Exceptions are never swallowed silently — each rejection is logged before the exception propagates.

```java
// Example from CreateWarehouseUseCase
if (location == null) {
    LOG.warnf("Creation rejected: location '%s' is not valid", warehouse.location);
    throw new WebApplicationException("Location '" + warehouse.location + "' is not valid.", 400);
}
```

### Structured Logging

All service classes use **JBoss Logger** (`org.jboss.logging.Logger`) — the standard for Quarkus — replacing all `System.out.println` calls found in pre-existing code.

Log levels are used consistently:

| Level | When |
|-------|------|
| `INFO` | Operation entry points and successful completions |
| `WARN` | Business rule rejections and expected failure paths |
| `DEBUG` | Repository queries and internal state |
| `ERROR` | Unexpected exceptions caught at the resource layer |

Logging was added to: `CreateWarehouseUseCase`, `ArchiveWarehouseUseCase`, `ReplaceWarehouseUseCase`, `WarehouseResourceImpl`, `WarehouseRepository`, `LocationGateway`, and `LegacyStoreManagerGateway`.

### Code Quality

- **Hexagonal Architecture** maintained throughout: domain use cases depend only on port interfaces; adapters implement those interfaces. No persistence leaks into the domain layer.
- **Single Responsibility**: each use case class handles exactly one operation.
- **`@Transactional`** on use cases ensures atomicity; the replace operation (archive + create) is an atomic unit.
- **Conventional Commits** used for all git history (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`).
- No unnecessary abstractions, helpers, or over-engineering added beyond the requirements.

### Coding Standards

- Java 17 — text blocks for multi-line JSON in tests, records where appropriate
- All new code follows the existing package/naming conventions in the project
- Field injection (`@Inject`) used consistently with the rest of the codebase

---

## 5. CI/CD Pipeline

A GitHub Actions workflow is defined in `.github/workflows/ci.yml`. It runs on every push and pull request to `main`/`master`.

### Pipeline Steps

```
Checkout → Setup JDK 17 (Temurin) → Build & Test (./mvnw verify) → Upload Artifacts
```

The pipeline uses a **native PostgreSQL 14 service container** (not Testcontainers) to avoid Docker socket permission issues common in CI environments:

```yaml
services:
  postgres:
    image: postgres:14
    env:
      POSTGRES_USER: quarkus_test
      POSTGRES_PASSWORD: quarkus_test
      POSTGRES_DB: quarkus_test
    ports:
      - 15432:5432
```

Quarkus Dev Services are explicitly disabled in the CI environment via environment variables:

```yaml
env:
  QUARKUS_DATASOURCE_DEVSERVICES_ENABLED: "false"
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://localhost:15432/quarkus_test
```

### Artifacts Uploaded

| Artifact | Retention |
|---|---|
| JaCoCo HTML report (`target/site/jacoco/`) | 30 days |
| Surefire test results (`target/surefire-reports/`) | 14 days |

---

## 6. Health Check

A MicroProfile Health readiness check is exposed at `GET /q/health/ready`, implemented in `WarehouseHealthCheck`.

```java
@Readiness
@ApplicationScoped
public class WarehouseHealthCheck implements HealthCheck {
  @Inject WarehouseStore warehouseStore;

  @Override
  public HealthCheckResponse call() {
    try {
      int activeCount = warehouseStore.getAll().size();
      return HealthCheckResponse.named("warehouse-store")
          .up()
          .withData("activeWarehouses", activeCount)
          .build();
    } catch (Exception e) {
      return HealthCheckResponse.named("warehouse-store")
          .down()
          .withData("error", e.getMessage())
          .build();
    }
  }
}
```

**Sample response (UP):**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "warehouse-store",
      "status": "UP",
      "data": { "activeWarehouses": 3 }
    }
  ]
}
```

The health check uses the domain port (`WarehouseStore`) — not the repository directly — keeping it consistent with the hexagonal architecture.

---

## 7. Case Study — Scenario Responses

Full responses are documented in [`case-study/CASE_STUDY.md`](case-study/CASE_STUDY.md). A summary of the key points raised for each scenario:

### Scenario 1 — Cost Allocation and Tracking

- The **Business Unit Code (BUC)** is the natural cost anchor. Every ledger entry must reference the BUC — not the database ID — so cost history remains traceable across warehouse generations (replace operations).
- Allocation rules (per m², per throughput, per headcount) must be agreed between Finance and Operations upfront; retroactive changes invalidate historical comparisons.
- Temporal granularity trade-off: real-time (per dispatch event) is accurate but costly; nightly batch is simpler but produces T+1 data.

### Scenario 2 — Cost Optimisation Strategies

- **Utilisation rate analysis** is the first lever: `capacity` and `stock` are already stored per warehouse. Warehouses below 40% utilisation are consolidation candidates — no new tooling required.
- Prioritisation framework: quick wins (utilisation data already available) → cross-location imbalance (requires time-series aggregation) → labour/transport reduction (requires TMS/WMS integration).
- Establish a baseline cost-per-unit-dispatched per warehouse; gate each optimisation with a payback period threshold.

### Scenario 3 — Integration with Financial Systems

- **Event-driven integration** is preferred: warehouse lifecycle events (created, replaced, archived) fire domain events. A financial adapter subscribes and updates the ERP — this mirrors the `StoreEvent` pattern already in the codebase (`@Observes(during = TransactionPhase.AFTER_SUCCESS)`), guaranteeing the ERP is notified only after a successful commit.
- Every integration message must carry a unique event ID for ERP-side deduplication (idempotency).
- A daily reconciliation job comparing active warehouses in this system against ERP cost centres detects drift before month-end close.

### Scenario 4 — Budgeting and Forecasting

- Standard linear extrapolation fails in fulfillment because demand is seasonal, warehouse replacements are lumpy capital events, and labour costs are step-function.
- The `replace` operation is itself a forecastable event: decommissioning cost + gap period cost + ramp-up cost profile should be modelled as planned events.
- **Rolling quarterly forecasts** are more actionable than annual budgets; actuals-vs-budget variance tracking is more valuable than the budget itself.

### Scenario 5 — Cost Control in Warehouse Replacement

- Cost records in the ERP must reference the specific **generation** of the BUC (old vs. new), not just the BUC string, to correctly attribute costs across the transition.
- The business rule "new warehouse stock must exactly match old warehouse stock" is a **financial control**, not merely an operational one: it prevents unexplained inventory variance in the P&L during transition.
- **Gap identified**: the current API has no "restore archived" operation. If a replacement is cancelled after archiving the old warehouse, Finance is left with a closed cost centre and no active one. This is a follow-up requirement worth raising.

---

## 8. How to Run Locally

### Prerequisites

- Java 17+
- Docker (for PostgreSQL via Testcontainers, or run manually as below)
- Maven wrapper included (`./mvnw`)

### Start PostgreSQL

```bash
docker run -d \
  --name quarkus_test \
  -e POSTGRES_USER=quarkus_test \
  -e POSTGRES_PASSWORD=quarkus_test \
  -e POSTGRES_DB=quarkus_test \
  -p 15432:5432 \
  postgres:14
```

### Run All Tests with Coverage

```bash
cd java-assignment
./mvnw verify
```

JaCoCo HTML report will be generated at `target/site/jacoco/index.html`.

### Run the Application (Dev Mode)

```bash
cd java-assignment
./mvnw quarkus:dev
```

The application starts at `http://localhost:8080`.

### Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/warehouse` | List all active warehouses |
| `GET` | `/warehouse/{buc}` | Get a warehouse by BUC |
| `POST` | `/warehouse` | Create a new warehouse |
| `DELETE` | `/warehouse/{buc}` | Archive a warehouse |
| `POST` | `/warehouse/{buc}/replacement` | Replace a warehouse |
| `GET` | `/q/health/ready` | Readiness health check |
| `GET` | `/q/health` | All health checks |

---

## Git Commit History

```
9138b78  fix: resolve test failures and achieve 80%+ JaCoCo coverage
b030db6  chore: add Maven wrapper and Docker build files
f052715  ci: add GitHub Actions workflow for build, test, and coverage
880f2bf  docs: answer QUESTIONS.md and document CASE_STUDY.md scenario discussions
e79f34c  test: add comprehensive test suite across all layers
c842006  feat: implemented all tasks
b25ff14  chore: add project base structure, guidelines, and case study briefing
```
