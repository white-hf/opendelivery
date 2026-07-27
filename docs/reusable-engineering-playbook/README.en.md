# Reusable Engineering Playbook

This directory contains no OpenDelivery business rules, table names, ports, or product scope. It can be copied into backend, Web, or multi-product projects as an engineering baseline.

## How to use it

Copy this directory when starting a project, then add project-specific PRDs, system design, data model, and API contracts. The repository `AGENTS.md` overrides this playbook for repository-specific commands, permissions, and security requirements.

## Map

1. [Architecture principles](architecture-principles.en.md): layers, dependency direction, domain boundaries, command/query separation.
2. [Development principles](development-principles.en.md): coding, data access, idempotency, audit, concurrency, and security.
3. [Testing and validation](testing-and-validation.en.md): unit, integration, E2E, performance, and isolated test data.
4. [Resilience and recovery](resilience-and-recovery.en.md): timeouts, retries, idempotency, degradation, compensation, and rollback.
5. [Non-functional baseline](non-functional-baseline.en.md): performance, observability, configuration, data security, and capacity.
6. [Delivery process](delivery-process.en.md): problem framing through release, retrospective, and iteration.
7. [Documentation standard](documentation-standard.en.md): PRD, design, iteration, test, summary, and ADR responsibilities.

## Minimum adoption set

- Root `AGENTS.md`.
- `docs/prd/`, `docs/design/`, `docs/iterations/`, and `docs/summaries/`.
- One current architecture ADR, one testing strategy, and one release/rollback process.
- Every iteration has a DoD, validation evidence, and explicit residual work.
