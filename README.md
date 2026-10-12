# MoveInSync Vendor & Driver Settlement Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-126%20Passed-brightgreen)](src/test/java)
[![Swagger UI](https://img.shields.io/badge/OpenAPI-Swagger%20UI-85EA2D?logo=swagger&logoColor=black)](http://localhost:8080/swagger-ui.html)

A financial settlement engine for MoveInSync's corporate commute network. Ingests trips, violations, and effective-dated rate cards to produce auditable driver payslips, automates invoice reconciliation with tolerance bands, and manages disputes via immutable delta adjustments.

---

## Quickstart

```bash
# Run locally with embedded H2 (zero setup)
.\mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # Linux / macOS

# Run with Docker + PostgreSQL 16
docker compose up -d --build
```

Swagger UI at **http://localhost:8080/swagger-ui.html**

---

## Test Accounts

| Username | Password | Role | Access |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Initiate settlements, submit invoices, view audit logs |
| `approver` | `approver123` | APPROVER | Finalize runs, reconcile invoices, approve/reject disputes |
| `employee` | `employee123` | EMPLOYEE | Driver 1 (Rajesh Kumar) — view own payslip, file disputes |
| `driver2` | `driver123` | EMPLOYEE | Driver 2 (Suresh Patel) — IDOR isolation test account |

---

## Pre-Seeded Reference Data

| Entity | ID | Notes |
|---|---|---|
| Vendor | `11111111-1111-1111-1111-111111111111` | QuickCabs Logistics Pvt Ltd |
| Business Unit | `22222222-2222-2222-2222-222222222222` | Bengaluru Outer Ring Road Campus |
| Driver 1 | `44444444-4444-4444-4444-444444444441` | Rajesh Kumar — mapped to `employee` |
| Driver 2 | `44444444-4444-4444-4444-444444444442` | Suresh Patel — mapped to `driver2` |
| Trip 1 | `66666666-6666-6666-6666-666666666661` | Oct 10 — rate ₹250.00 |
| Trip 2 | `66666666-6666-6666-6666-666666666662` | Oct 16 — rate ₹280.00 (post rate-hike) |
| Violation | `77777777-7777-7777-7777-777777777771` | OVER_SPEED on Trip 2, penalty ₹500.00 |

---

## REST API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/health` | Public | Service heartbeat |
| `GET` | `/actuator/health` | Public | Spring Boot health probe |
| `GET` | `/swagger-ui.html` | Public | Interactive API docs |
| `POST` | `/api/v1/settlement-runs` | ADMIN, APPROVER | Calculate settlement run; idempotent replay on repeat |
| `GET` | `/api/v1/settlement-runs` | ADMIN, APPROVER | List all runs (`?page=0&size=20`) |
| `GET` | `/api/v1/settlement-runs/{id}` | ADMIN, APPROVER | Get run details and financial totals |
| `POST` | `/api/v1/settlement-runs/{id}/approve` | ADMIN, APPROVER | Finalize run (`CALCULATED` → `FINALIZED`) |
| `GET` | `/api/v1/drivers/{driverId}/payslips/{billingPeriod}` | EMPLOYEE (own), APPROVER, ADMIN | Itemized payslip with `isReconciled` proof. IDOR protected. |
| `POST` | `/api/v1/vendor-invoices` | ADMIN, APPROVER | Submit vendor tax invoice |
| `GET` | `/api/v1/vendor-invoices` | ADMIN, APPROVER | List invoices (`?vendorId=&billingPeriod=`) |
| `GET` | `/api/v1/vendor-invoices/{id}` | ADMIN, APPROVER | Get invoice by ID |
| `POST` | `/api/v1/reconciliations` | ADMIN, APPROVER | Reconcile finalized run vs invoice with tolerance threshold |
| `GET` | `/api/v1/reconciliations/{id}` | ADMIN, APPROVER | Get reconciliation result and variance |
| `POST` | `/api/v1/disputes` | EMPLOYEE, ADMIN, APPROVER | File dispute against one trip XOR one violation |
| `GET` | `/api/v1/disputes` | EMPLOYEE, ADMIN, APPROVER | List disputes (`?settlementId=`) |
| `GET` | `/api/v1/disputes/{id}` | EMPLOYEE, ADMIN, APPROVER | Get dispute by ID |
| `POST` | `/api/v1/disputes/{id}/approve` | ADMIN, APPROVER | Approve dispute — creates `Adjustment` for next-month ingestion |
| `POST` | `/api/v1/disputes/{id}/reject` | ADMIN, APPROVER | Reject dispute with reviewer notes |
| `GET` | `/api/v1/adjustments` | EMPLOYEE, ADMIN, APPROVER | List delta adjustments (`?settlementId=`) |
| `GET` | `/api/v1/adjustments/{id}` | EMPLOYEE, ADMIN, APPROVER | Get adjustment by ID |
| `GET` | `/api/v1/audit-logs` | ADMIN | Full immutable audit trail |
| `GET` | `/api/v1/audit-logs/entity/{type}/{id}` | ADMIN, APPROVER | Audit history for a specific entity |

---

## Evaluation Walkthrough

### 1 — Run Settlement
```bash
curl -s -u admin:admin123 -X POST http://localhost:8080/api/v1/settlement-runs \
  -H "Content-Type: application/json" \
  -d '{"vendorId":"11111111-1111-1111-1111-111111111111","businessUnitId":"22222222-2222-2222-2222-222222222222","billingPeriod":"2026-10"}'
```
First call → `201 Created`, `idempotentReplay: false`. Repeat → `200 OK`, `idempotentReplay: true`. Save `id` as `RUN_ID`.

### 2 — View Payslip (Employee)
```bash
curl -s -u employee:employee123 \
  http://localhost:8080/api/v1/drivers/44444444-4444-4444-4444-444444444441/payslips/2026-10
```
- Trip 1 → ₹250.00, Trip 2 → ₹280.00 (mid-month rate change applied correctly)
- SLA Bonus → +₹2,000.00 | Speed Penalty → -₹500.00
- `"isReconciled": true` — lines sum equals net payout

Save `settlementId` as `SETTLEMENT_ID`.

**IDOR test** (must return `403`):
```bash
curl -s -u driver2:driver123 \
  http://localhost:8080/api/v1/drivers/44444444-4444-4444-4444-444444444441/payslips/2026-10
```

### 3 — Approve Run
```bash
curl -s -u approver:approver123 -X POST \
  http://localhost:8080/api/v1/settlement-runs/$RUN_ID/approve
```

### 4 — Submit Invoice & Reconcile
```bash
curl -s -u admin:admin123 -X POST http://localhost:8080/api/v1/vendor-invoices \
  -H "Content-Type: application/json" \
  -d '{"vendorId":"11111111-1111-1111-1111-111111111111","businessUnitId":"22222222-2222-2222-2222-222222222222","billingPeriod":"2026-10","invoiceNumber":"INV-2026-10-001","claimedAmount":2000.00,"invoiceDate":"2026-10-31"}'
```
Save `id` as `INVOICE_ID`.

```bash
curl -s -u approver:approver123 -X POST http://localhost:8080/api/v1/reconciliations \
  -H "Content-Type: application/json" \
  -d '{"settlementRunId":"'$RUN_ID'","vendorInvoiceId":"'$INVOICE_ID'","toleranceThreshold":100.00}'
```
Result → `MATCHED_WITHIN_TOLERANCE` (|₹2000 − ₹2030| = ₹30 ≤ ₹100 tolerance).

### 5 — Dispute & Approve
```bash
curl -s -u employee:employee123 -X POST http://localhost:8080/api/v1/disputes \
  -H "Content-Type: application/json" \
  -d '{"settlementId":"'$SETTLEMENT_ID'","disputedViolationId":"77777777-7777-7777-7777-777777777771","reason":"False GPS alert","claimedDelta":500.00}'
```
Save `id` as `DISPUTE_ID`.

```bash
curl -s -u approver:approver123 -X POST http://localhost:8080/api/v1/disputes/$DISPUTE_ID/approve \
  -H "Content-Type: application/json" \
  -d '{"approvedDelta":500.00,"notes":"Verified telematics logs"}'
```
Creates `Adjustment` (PENALTY_WAIVER, +₹500.00). October payslip unchanged. November run ingests it and marks it `DISBURSED`.

---

## Key Financial Guarantees

| Guarantee | Implementation |
|---|---|
| No float drift | Pure `BigDecimal` (scale 2, `HALF_UP`) — no `float`/`double` for money |
| Payslip proof | `Σ signed lines == netPayout` verified on every payslip (`isReconciled: true`) |
| Idempotency | One durable claim per `(vendor, BU, period)` — concurrent duplicates → `409 Conflict` |
| Immutability | Approved disputes create new `Adjustment` records; past payslips never modified |
| Mid-month rates | `[effectiveFrom, effectiveTo)` resolved at trip's exact `completedAt` timestamp |
| Progressive TDS | 5% withheld only on cumulative gross > ₹30,000 (Section 194C); prior runs offset |
| Penalty caps | Excess above contractual ceiling credited as `PENALTY_CAP_ADJUSTMENT` line |

---

## Tests

```bash
.\mvnw.cmd test    # Windows
./mvnw test        # Linux / macOS
```
```
Tests run: 126, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Covers: domain math units · Flyway schema validation · concurrency/race-condition · IDOR security · dispute lifecycle · failure recovery & retry.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3.4 (Web, JPA, Security, Actuator) |
| Database | PostgreSQL 16 (prod) / H2 (local & test) |
| Migrations | Flyway 10 — 6 versioned migrations (V1–V6) |
| Security | Spring Security 6 — HTTP Basic, `@PreAuthorize`, IDOR SpEL |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Containers | Docker multi-stage, non-root user (UID 1001) |
