# Repository Guidelines

> 开发 agent 入职导读（阅读地图/架构/约束/并行规则）：`docs/agent-onboarding.md`（[EN](docs/agent-onboarding.en.md)）。

## Project Structure & Module Organization

This Java 17/Spring Boot 3.3 monorepo provides decoupled multi-service architectures:

- `driver/`: Driver domain services including `easydelivery-auth`, `easydelivery-delivery`, `easydelivery-scan`, and `easydelivery-driver-api` (Port 9000).
- `operations/`: Operations domain including `easydelivery-ops-api` (Port 9001) and `easydelivery-operations-web` (React/Vite Operations Web UI).
- `easydelivery-common`: Shared entities, DTOs, response types, exceptions, repositories, Flyway migrations, and in-memory store.

Production code belongs in each module's `src/main/java`; tests mirror packages under `src/test/java`. Current governed documents live in `docs/`; `doc/` contains legacy API notes. Do not edit or commit generated `target/` contents.

### Documentation & PRD Governance Rules
Documentation follows a strict two-level structure in `docs/`:
1. **Product Requirement Documents (PRD)** (`docs/prd/`): Long-lived, authoritative system baselines (e.g. `docs/prd/operations-web-specification.md`). PRDs define overall system architecture, global features, user roles, and page specifications. When introducing or changing product capabilities, the corresponding PRD baseline in `docs/prd/` MUST be updated.
2. **Iteration Documents** (`docs/<domain>/iterations/`): Short-lived sprint specifications (e.g. `docs/operations/iterations/iteration-r06-sla-wave-dispatch.md`). Iterations define specific sprint tasks, API deltas, boundaries, and DoD. Iteration specs are marked `REVIEWED` before coding, and updated with summaries upon completion.

Do not combine PRD baseline updates, iteration specs, and code implementations into an unreviewed single step.

## Build, Test, and Development Commands

Use the bundled Maven through the helper script:

- `./run.sh test` runs all JUnit tests across the reactor.
- `./run.sh build` creates module JARs while skipping tests.
- `./run.sh run` starts the Operations API on port `9001`.
- `./run.sh run-driver` starts the Driver API on port `9000`.
- `./run.sh docker-build` builds the JAR and Docker image.
- `./run.sh docker-up` starts the container; `./run.sh docker-down` stops it.

For a complete pre-PR check, run `./tools/apache-maven-3.9.8/bin/mvn clean verify`.
Database changes require a new Flyway migration and `DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh`.

## Coding Style & Naming Conventions

Use four-space indentation, braces on the declaration line, and packages rooted at `com.hf.easydelivery`. Use `PascalCase` for classes, `camelCase` for methods and variables, and `UPPER_SNAKE_CASE` for constants. Keep controllers thin; place reusable DTOs, stores, repositories, and cross-cutting behavior in `easydelivery-common`. Preserve request-field names from the Android API contract. No formatter or linter is configured, so match nearby code.

## Data Access & Persistence

Persistence follows `docs/design/persistence-architecture.md` (ADR): Spring Data JPA for entity lifecycle (command side); `JdbcTemplate`/native SQL only for set-based `INSERT…SELECT`, dialect upserts, spatial functions, and reporting queries, each with an escape-hatch comment. Flyway is the only schema source (`spring.jpa.hibernate.ddl-auto=none`); entities reference related rows by id (no association navigation); `version` columns map to `@Version`; locked reads use repository `@Lock(PESSIMISTIC_WRITE)` methods. New contexts place entities and repositories in `<context>.<subdomain>.persistence` and migrate per the ADR playbook — the T01 arrival domain (`operations/arrival/persistence/`, `PhysicalArrivalService`) is the reference implementation.

## Testing Guidelines

Tests use JUnit 5 through `spring-boot-starter-test`. Name test classes `*Test.java`, mirror the production package, and use behavior-focused method names such as `returnsTokenForValidCredentials`. Add unit tests for new shared logic and controller tests for changed HTTP behavior. There is no enforced coverage threshold; cover success, validation, authentication, and failure paths relevant to the change.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout. Use short, imperative commit subjects, optionally with a module scope, for example `scan: validate batch status`. Keep commits focused. Pull requests should explain the behavior change, list affected modules and verification commands, link related issues, and include sample requests/responses for API changes. Call out configuration, port, or Docker changes explicitly.

## Security & Configuration

Do not commit production secrets or tokens. Supply `JWT_SECRET` through the environment and treat values in Docker configuration as development defaults only. Avoid logging credentials, bearer tokens, or uploaded proof-of-delivery content.
