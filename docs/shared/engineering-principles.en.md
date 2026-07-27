# Engineering and Architecture Principles

This is the reusable engineering entry point for OpenDelivery and similar projects. New contributors should read it, then follow the repository `AGENTS.md`, product requirements, and domain-specific design documents.

## 1. Understand the business first

Confirm users, the closed-loop workflow, state machines, data sources, authorization boundaries, and acceptance evidence before coding. Product changes update the PRD/system design first, then a `REVIEWED` iteration document.

## 2. Layers and dependency direction

```text
Controller → Application Service → Domain → Persistence
                         ↘ Query Service → Query Repository/Read Adapter
```

- Controllers handle HTTP, auth context, and response envelopes only.
- Application services orchestrate use cases and transactions.
- Domain code owns state machines, policies, and invariants, without SQL.
- Command persistence uses JPA entities and repositories.
- Query persistence uses projection DTOs; complex SQL belongs only in query repositories/read adapters.

## 3. Data-access rules

- Flyway is the only schema source; Hibernate must not create tables.
- Entities reference related rows by id to avoid implicit lazy loading and N+1 queries.
- `JdbcTemplate`/native SQL is an explicitly documented escape hatch for spatial functions, set-based writes, dialect upserts, and aggregate/reporting reads.
- Every large query documents indexes, pagination, expected volume, and execution-plan considerations.

## 4. Delivery quality gates

Every iteration includes bilingual documentation, unit tests, API/integration tests, real-database E2E where relevant, an execution summary, and rollback notes. New capabilities default to idempotency, audit logs, authorization, station isolation, optimistic locking, and localization.

## 5. Reuse boundary

Project-specific ports, tables, states, and workflows belong in project PRDs/design documents. The layering, persistence, testing, and delivery rules above can be reused directly in a new project.

Details: [Persistence ADR](../design/persistence-architecture.en.md); execution plan: [T05 query-layer refactoring](iterations/iteration-t05-query-layer-refactoring.en.md).
