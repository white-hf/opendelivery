# Repository Guidelines

## Project Structure & Module Organization

This Java 17/Spring Boot 3.3 service is a Maven multi-module monolith:

- `easydelivery-common`: shared DTOs, response types, exceptions, authentication helpers, repositories, and the in-memory data store.
- `easydelivery-auth`: login, registration, and token-related endpoints.
- `easydelivery-delivery`: parcel task and proof-of-delivery APIs.
- `easydelivery-scan`: barcode scanning and batch workflows.
- `easydelivery-app`: application entry point, web configuration, and `application.properties`.

Production code belongs in each module's `src/main/java`; tests mirror packages under `src/test/java`. Current governed documents live in `docs/`; `doc/` contains legacy API notes. Do not edit or commit generated `target/` contents.

Documentation is split by product: `docs/driver/` for Driver App/API, `docs/operations/` for Operations Web/API, and `docs/shared/` for cross-product state, data, and joint gates. Before implementing product behavior, update the relevant bilingual product/design/iteration documents and record the iteration as `REVIEWED`. Then implement, test, and add an execution summary. Follow `docs/document-governance.md`; do not combine documentation review and unreviewed feature development into one implicit step.

## Build, Test, and Development Commands

Use the bundled Maven through the helper script:

- `./run.sh test` runs all JUnit tests across the reactor.
- `./run.sh build` creates module JARs while skipping tests.
- `./run.sh run` builds when necessary, then starts the API on port `9000`.
- `./run.sh docker-build` builds the JAR and Docker image.
- `./run.sh docker-up` starts the container; `./run.sh docker-down` stops it.

For a complete pre-PR check, run `./tools/apache-maven-3.9.8/bin/mvn clean verify`.
Database changes require a new Flyway migration and `DB_PASSWORD='<secret>' scripts/mysql-e2e-test.sh`.

## Coding Style & Naming Conventions

Use four-space indentation, braces on the declaration line, and packages rooted at `com.hf.easydelivery`. Use `PascalCase` for classes, `camelCase` for methods and variables, and `UPPER_SNAKE_CASE` for constants. Keep controllers thin; place reusable DTOs, stores, repositories, and cross-cutting behavior in `easydelivery-common`. Preserve request-field names from the Android API contract. No formatter or linter is configured, so match nearby code.

## Testing Guidelines

Tests use JUnit 5 through `spring-boot-starter-test`. Name test classes `*Test.java`, mirror the production package, and use behavior-focused method names such as `returnsTokenForValidCredentials`. Add unit tests for new shared logic and controller tests for changed HTTP behavior. There is no enforced coverage threshold; cover success, validation, authentication, and failure paths relevant to the change.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout. Use short, imperative commit subjects, optionally with a module scope, for example `scan: validate batch status`. Keep commits focused. Pull requests should explain the behavior change, list affected modules and verification commands, link related issues, and include sample requests/responses for API changes. Call out configuration, port, or Docker changes explicitly.

## Security & Configuration

Do not commit production secrets or tokens. Supply `JWT_SECRET` through the environment and treat values in Docker configuration as development defaults only. Avoid logging credentials, bearer tokens, or uploaded proof-of-delivery content.
