# Modular ERP POC

A proof of concept for a multi-tenant, modular ERP backend built with Java 21, Spring Boot and Spring Modulith.

> **Status:** educational proof of concept. It has not been security-hardened or validated for production use.

## What it demonstrates

- A modular monolith with explicit domain boundaries and ArchUnit/Modulith checks.
- Schema-per-tenant isolation with PostgreSQL and Flyway.
- JWT authentication, role-based permissions, step-up authentication and audit events.
- Brazilian business domains represented as separate modules: registrations, products, inventory, commercial movements, purchasing, finance, service orders, contracts and fiscal integration.
- REST/OpenAPI endpoints, idempotent workflows and domain events.
- Unit tests plus integration tests with Testcontainers and WireMock.

The default fiscal provider is an in-memory implementation intended for development and tests. Any external provider, certificate store or real fiscal workflow requires separate credentials, legal validation and operational hardening.

## Stack

- Java 21 and Spring Boot 3.5
- Spring Modulith, Spring Security and Spring Data JPA
- PostgreSQL 16 and Flyway
- Testcontainers, JUnit 5, ArchUnit, WireMock and JaCoCo

## Run locally

Requirements: Java 21 and PostgreSQL 16. Docker is optional for starting PostgreSQL and is required for the integration suite.

```bash
docker run --name modular-erp-poc-db \
  -e POSTGRES_DB=modular_erp_poc \
  -e POSTGRES_USER=modular_erp_poc \
  -e POSTGRES_PASSWORD=modular_erp_poc \
  -p 5432:5432 \
  -d postgres:16-alpine

SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`; Swagger UI is available at `http://localhost:8080/swagger-ui.html`. Override local defaults through the `ERP_POC_*` variables documented in `src/main/resources/application.yml`.

The tenant provisioning endpoint is disabled unless `ERP_POC_SECURITY_BOOTSTRAP_TOKEN` is explicitly set. Replace the local JWT secret before using the application outside an isolated development environment.

## Verify

```bash
./mvnw -B -ntp spotless:check
./mvnw -B -ntp -Dskip.failsafe=true test
./mvnw -B -ntp verify
```

Integration tests automatically skip on machines where Docker is unavailable. The CI workflow runs the complete suite on Linux with Docker.

## Repository map

- `src/main/java/dev/dimi/erp/`: application modules and shared infrastructure.
- `src/main/resources/db/migration/`: global and per-tenant Flyway migrations.
- `docs/architecture/`: architectural and engineering decisions.
- `docs/domain/`: domain scope and business rules implemented by the POC.

All names, people, companies and Brazilian document numbers used in tests are synthetic fixtures. They must never be treated as production or customer data.

Some documents describe target architecture and future capabilities in addition to what is already implemented. Executable code, migrations and tests are the source of truth for the current release.

## Security scope

Deployment manifests and runtime credentials are outside this project's scope. CI performs secret scanning and reviews dependency changes on pull requests. The application still requires a dedicated threat model, full SCA/SAST/DAST coverage, authorization review, secret rotation strategy, distributed rate limiting and external penetration testing before any production use.

## Authorship and license

Project-specific implementation is copyright © 2026 Matheus Silva. Automated dependency updates may be attributed to Dependabot where applicable. Third-party libraries, tools and trademarks remain subject to their respective terms. See [NOTICE.md](NOTICE.md).

This project is available under the [MIT License](LICENSE).
