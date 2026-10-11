# MoveInSync Vendor & Driver Settlement Engine

[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-113%20Passed-brightgreen)](file:///d:/Vendor%20&%20Driver%20Settlement%20Engine)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?logo=docker&logoColor=white)](file:///d:/Vendor%20&%20Driver%20Settlement%20Engine/Dockerfile)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)](LICENSE)

An enterprise-grade financial settlement, reconciliation, and audit engine designed for MoveInSync's corporate commute transport network. The engine calculates monthly driver payouts, generates itemized payslips, reconciles company disbursements against vendor tax invoices with configurable tolerance bands, resolves trip disputes via immutable delta adjustments, and maintains an append-only audit trail with distributed correlation tracing.

---

## Architecture Overview

```text
                                  [ Client / ERP / Gateway ]
                                               │
                                 HTTP Basic + X-Correlation-ID
                                               ▼
                              ┌──────────────────────────────────┐
                              │     Security & Auth Filter       │
                              │ (RBAC: ADMIN, APPROVER, EMPLOYEE)│
                              └────────────────┬─────────────────┘
                                               │
                                               ▼
                              ┌──────────────────────────────────┐
                              │      CorrelationIdFilter         │
                              │   (MDC: traceId, correlationId)  │
                              └────────────────┬─────────────────┘
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               ▼                               ▼                               ▼
     [ Settlement Run Engine ]      [ Reconciliation Engine ]       [ Dispute & Adjustment ]
     • Effective-dated contracts    • Tax invoice intake            • Non-destructive deltas
     • Volume incentives (slabs)    • Tolerance matching (₹100)     • Isolated adjustments
     • Capped SLA penalties         • Discrepancy classification    • Ledger immutability
     • Progressive 5% TDS (₹30k)    • Pair-level idempotency        • Re-approval idempotency
               │                               │                               │
               └───────────────────────────────┼───────────────────────────────┘
                                               │
                                               ▼
                              ┌──────────────────────────────────┐
                              │    Append-Only Audit Trail       │
                              │   (Action, Entity, User, Role)   │
                              └────────────────┬─────────────────┘
                                               │
                                               ▼
                              ┌──────────────────────────────────┐
                              │      PostgreSQL 16 Database      │
                              │  (Flyway Migrations V1 -> V4)    │
                              └──────────────────────────────────┘
```

---

## Key Capabilities & Financial Invariants

1. **Deterministic Financial Mathematics**:
   - Zero floating-point arithmetic. All calculations use `java.math.BigDecimal` with explicit scale `2` and `RoundingMode.HALF_UP`.
   - **Strict Summation Invariant**: $\sum \text{SignedLineItems} = \text{NetPayout}$.
2. **Effective-Dated Contracts & Mid-Month Rate Updates**:
   - Rate resolution evaluates the trip's completion timestamp against contract effective intervals (`effective_from` / `effective_to`), correctly apportioning earnings before and after mid-month renegotiations (e.g., October 15th).
3. **Progressive Monthly TDS Withholding**:
   - Evaluates cumulative gross driver earnings across the monthly billing cycle against the Indian Income Tax threshold (default: ₹30,000.00). Applies 5% tax withholding strictly on incremental income once the ceiling is breached.
4. **Volume Tier Incentives**:
   - Progressively awards tiered trip incentives (e.g., Tier 1: 50+ trips = ₹1,500; Tier 2: 100+ trips = ₹4,000) based on completed trips within the settlement cycle.
5. **Contractual Penalty Caps**:
   - Enforces per-violation penalties (e.g., ₹200 for late arrivals > 15 mins) bounded by contract maximum deductions (e.g., ₹2,500 monthly cap).
6. **Composite Idempotency & Concurrency Isolation**:
   - Composite unique database constraints on `(vendor_id, business_unit_id, billing_period)` and `(settlement_run_id, vendor_invoice_id)` guarantee run-twice safety and eliminate duplicate payouts or race conditions.
7. **Tolerance-Based Invoice Reconciliation**:
   - Ingests external vendor tax invoices, matches claims against ledger disbursements within configurable tolerance thresholds (default: ₹100.00), and classifies outcomes into `MATCHED`, `DISCREPANT`, or `MANUAL_REVIEW_REQUIRED`.
8. **Immutable Dispute Resolution & Delta Adjustments**:
   - Finalized monthly settlements are strictly immutable. Approved disputes create isolated `Adjustment` records credited to future disbursement runs without rewriting historical ledgers.
9. **Fine-Grained RBAC & Resource-Level Authorization**:
   - Route-level role enforcement (`ROLE_ADMIN`, `ROLE_APPROVER`, `ROLE_EMPLOYEE`) coupled with Spring SpEL evaluation (`@settlementSecurity.canAccessDriver(...)`) preventing Insecure Direct Object References (IDOR) on driver payslips.
10. **Distributed Observability & Telemetry**:
    - Automatic `X-Correlation-ID` header generation and SLF4J MDC binding.
    - Custom Micrometer Prometheus metrics under `/actuator/prometheus` tracking run latencies, driver throughput, and discrepancy distribution.
    - Bounded query pagination (`max 100`) preventing memory exhaustion on month-end queries.

---

## Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Runtime** | Java 21 LTS (Temurin) | Core language runtime (records, pattern matching, virtual-thread capable) |
| **Framework** | Spring Boot 3.3.4 | Dependency injection, MVC, Security, Actuator |
| **Security** | Spring Security 6 | HTTP Basic Auth, method-level security (`@PreAuthorize`), SpEL evaluators |
| **Persistence** | Spring Data JPA / Hibernate 6 | Relational mapping, pessimistic row locking |
| **Database** | PostgreSQL 16 / H2 | Relational database (H2 for zero-dependency local development) |
| **Migrations** | Flyway 10 | Versioned, forward-only schema migrations (V1 to V4) |
| **Observability** | Micrometer + Prometheus | Distributed correlation tracing, runtime telemetry, metrics scraping |
| **Documentation** | SpringDoc OpenAPI 3 / Swagger UI | Interactive API documentation |
| **Containers** | Docker (Multi-stage) & Docker Compose | Hardened container packaging with non-root user (UID 1001) |

---

## Quickstart Guide

### Option 1: Run Locally (Zero External Dependencies)
The local profile uses an embedded H2 database running in PostgreSQL compatibility mode:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Once started:
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health & Probes**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Prometheus Metrics**: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

### Option 2: Run with Docker Compose (Production Setup)
Deploys PostgreSQL 16 with persistent disk storage alongside the hardened containerized application:

```bash
# 1. Setup environment configuration
cp .env.example .env

# 2. Build and launch services in background
docker compose up -d --build

# 3. Stream application logs
docker compose logs -f settlement-app
```

To stop services:
```bash
docker compose down
```

---

## Role-Based Authentication Matrix

The application secures endpoints using HTTP Basic Authentication:

| Username | Default Password | Role | Permitted Actions |
|---|---|---|---|
| `admin` | `admin123` | `ROLE_ADMIN` | Full system access: trigger runs, approve, reconcile, view all audit logs |
| `approver` | `approver123` | `ROLE_APPROVER` | Approve settlement runs, resolve disputes, view entity audit history |
| `employee` | `employee123` | `ROLE_EMPLOYEE` | View settlements, submit disputes, download own driver payslips |
| `driver2` | `driver123` | `ROLE_EMPLOYEE` | Restricted driver identity (verifies IDOR security policy) |

---

## End-to-End API Walkthrough

### 1. Trigger Monthly Settlement Run
```bash
curl -X POST http://localhost:8080/api/v1/settlement-runs \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": "11111111-1111-1111-1111-111111111111",
    "businessUnitId": "22222222-2222-2222-2222-222222222222",
    "billingPeriod": "2026-10"
  }'
```

### 2. Approve Settlement Run
```bash
curl -X POST http://localhost:8080/api/v1/settlement-runs/{settlementRunId}/approve \
  -u approver:approver123
```

### 3. Retrieve Itemized Driver Payslip
```bash
curl -X GET http://localhost:8080/api/v1/settlements/{settlementId} \
  -u employee:employee123
```

### 4. Ingest Vendor Tax Invoice
```bash
curl -X POST http://localhost:8080/api/v1/vendor-invoices \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": "11111111-1111-1111-1111-111111111111",
    "businessUnitId": "22222222-2222-2222-2222-222222222222",
    "invoiceNumber": "INV-2026-10-001",
    "billingPeriod": "2026-10",
    "claimedAmount": 2530.00,
    "invoiceDate": "2026-10-31"
  }'
```

### 5. Reconcile Invoice Against System Ledger
```bash
curl -X POST http://localhost:8080/api/v1/reconciliations \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "settlementRunId": "{settlementRunId}",
    "vendorInvoiceId": "{vendorInvoiceId}",
    "tolerance": 100.00
  }'
```

### 6. File & Approve a Trip Dispute
```bash
# File dispute
curl -X POST http://localhost:8080/api/v1/disputes \
  -u employee:employee123 \
  -H "Content-Type: application/json" \
  -d '{
    "settlementId": "{settlementId}",
    "targetEntityType": "TRIP",
    "targetEntityId": "{tripId}",
    "claimedDeltaAmount": 150.00,
    "disputeReason": "Missing toll allowance compensation"
  }'

# Approve dispute and generate isolated delta adjustment
curl -X POST http://localhost:8080/api/v1/disputes/{disputeId}/approve \
  -u approver:approver123 \
  -H "Content-Type: application/json" \
  -d '{ "resolutionNote": "Toll toll receipt verified and validated" }'
```

### 7. Inspect Audit Trail
```bash
curl -X GET "http://localhost:8080/api/v1/audit-logs?page=0&size=20" \
  -u admin:admin123
```

---

## Automated Test Suite

The engine is covered by an automated test suite verifying edge cases, financial accuracy, concurrency, and security:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

### Test Suite Summary:
- **Total Tests**: `113`
- **Pass Rate**: `100%` (0 Failures, 0 Errors, 0 Skipped)
- **Coverage Areas**:
  - Deterministic Calculation: Effective-dated rates, volume slabs, penalty caps, progressive TDS.
  - Transactional Orchestration: Concurrency locks, idempotent re-execution under thread races.
  - Invoice Reconciliation: Exact matching, tolerance boundaries, discrepancy classification.
  - Disputes & Adjustments: Delta generation, ledger immutability, re-approval idempotency.
  - Security & Authorization: Negative RBAC tests, driver payslip IDOR isolation.
  - Observability & Reliability: `X-Correlation-ID` propagation, bounded pagination invariants, bulk calculation benchmark.

---

## Database Migrations (Flyway)

| Migration | Scope | Key Tables Created |
|---|---|---|
| `V1__init_settlement_schema.sql` | Core schema | `vendors`, `business_units`, `drivers`, `trips`, `trip_tolls`, `violations`, `vendor_contracts`, `contract_rules` |
| `V2__create_settlement_tables.sql` | Ledgers | `settlement_runs`, `settlements`, `settlement_lines`, `vendor_invoices`, `reconciliations`, `disputes`, `adjustments` |
| `V3__seed_demo_data.sql` | Demo seed data | Seeded vendors, business units, drivers, contracts, trips for local simulation |
| `V4__create_audit_logs.sql` | Audit logging | `audit_logs` append-only table with entity and action indexing |

---

## Project Structure

```text
moveinsync-settlement-engine/
├── .github/workflows/ci.yml       # GitHub Actions CI/CD Pipeline
├── Dockerfile                      # Multi-stage non-root container definition
├── compose.yaml                    # Docker Compose (PostgreSQL 16 + App)
├── .dockerignore                   # Docker build context exclusions
├── .gitignore                      # Git tracking exclusions
├── .env.example                    # Environment variable template
├── pom.xml                         # Maven dependencies & build plugins
├── src/
│   ├── main/
│   │   ├── java/com/moveinsync/settlement/
│   │   │   ├── config/             # Security, OpenAPI, Filter configurations
│   │   │   ├── controller/         # REST API Controllers (Swagger annotated)
│   │   │   ├── dto/                # Request & Response Data Transfer Objects
│   │   │   ├── entity/             # JPA Domain Entities
│   │   │   ├── exception/          # RFC 7807 Global Exception Handling
│   │   │   ├── repository/         # Spring Data JPA Repositories (Pageable)
│   │   │   ├── security/           # Fine-Grained Authorization Evaluators
│   │   │   └── service/            # Financial & Orchestration Services
│   │   └── resources/
│   │       ├── application.yml     # Core Spring Configuration
│   │       ├── application-prod.yml# Production Profile Configuration
│   │       └── db/migration/       # Flyway SQL Migration Scripts (V1 - V4)
│   └── test/java/com/moveinsync/settlement/ # 113 Automated Integration Tests
└── README.md                       # Comprehensive Project Documentation
```
