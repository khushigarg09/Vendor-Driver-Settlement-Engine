# MoveInSync Vendor & Driver Settlement Engine

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)](LICENSE)

An enterprise-grade financial settlement, reconciliation, and audit platform built for MoveInSync's corporate commute network. The engine calculates monthly driver earnings, vendor payable disbursements, and reconciles payouts against vendor invoices with zero decimal drift.

---

## Key Features

1. **Deterministic Financial Math:** Pure `BigDecimal` implementation with explicit scale 2 and `HALF_UP` rounding. Strict signed line summation invariant ($\sum \text{SignedLines} = \text{Net Payout}$).
2. **Effective-Dated Contracts:** Point-in-time rate resolution honoring mid-month rate updates (e.g. October 15th) based on trip completion timestamp.
3. **Cumulative Monthly TDS Withholding:** Threshold-aware incremental tax withholding across the monthly billing cycle.
4. **Idempotent Settlement Runs:** Run-twice safe execution guaranteed by PostgreSQL composite unique constraints and transactional row locks.
5. **Vendor Invoice Reconciliation:** Automated aggregation by vendor and business unit with configurable tolerance discrepancy flagging.
6. **Immutable Disputes & Delta Adjustments:** Standalone delta adjustments for approved trip/penalty disputes without altering historical finalized settlements.
7. **Role-Based Access Control (RBAC):** Spring Security 6 integration with `ROLE_ADMIN`, `ROLE_APPROVER`, and `ROLE_EMPLOYEE`.

---

## Technology Stack

- **Language:** Java 21 (OpenJDK LTS)
- **Framework:** Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Actuator)
- **Database:** PostgreSQL 15+ (Production) / H2 (Local zero-dependency profile)
- **Migrations:** Flyway
- **Documentation:** SpringDoc OpenAPI 3 / Swagger UI
- **Build Tool:** Apache Maven & Maven Wrapper

---

## Getting Started

### Prerequisites
- **Java 21 JDK** installed and available in your environment (`java -version`).
- **Docker & Docker Compose** (Optional, for containerized PostgreSQL).

### Running the Application Locally
The application defaults to the `local` profile with an in-memory database, requiring no external dependencies to run:

```bash
# Using Maven Wrapper (Windows PowerShell)
.\mvnw.cmd spring-boot:run

# Using Maven Wrapper (macOS / Linux)
./mvnw spring-boot:run
```

Once running, access:
- **Application Health:** [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Running Automated Tests

Run the complete test suite:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

---

## Environment Variables

Copy `.env.example` to `.env` to customize production connection settings:

| Variable | Description | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active configuration profile (`local`, `prod`) | `local` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database name | `settlement_db` |
| `DB_USER` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `SETTLEMENT_RECONCILIATION_TOLERANCE_RUPEES` | Invoice matching tolerance | `100.00` |
| `SETTLEMENT_TDS_MONTHLY_THRESHOLD_RUPEES` | Monthly TDS tax exemption ceiling | `30000.00` |
