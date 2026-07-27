# T05 Query-Layer and ORM Command-Side Refactoring

> Status: `COMPLETED` (2026-07-27); T05-A/B/C/D are complete; scope: Operations API and Driver API query layering, with no public API behavior change.

## Goal

Move service query SQL into dedicated Query Repositories/read adapters, use JPA entities and repositories for command writes, and keep persistence out of controllers.

## Technical approach

- Simple reads: Spring Data derived queries, JPQL, and projection DTOs.
- Maps, spatial functions, and aggregates: JdbcTemplate/native SQL under `persistence.query`, with an escape-hatch rationale.
- Commands: entities map existing tables; repositories handle save, state changes, and locks; entities reference related rows by id.
- Responses: dedicated query DTOs; never serialize entities as page responses.

## Slices

1. T05-A: Dispatch Wave command and query separation (completed; see summary).
2. T05-B: Planning Parcel/Driver Capacity query repositories (completed; see summary).
3. T05-C: Case, Handover, and Day Close query migration (completed; see summary).
4. T05-D: Driver task and delivery query migration (completed; see summary).

## Acceptance

- No SQL strings in controllers or application services.
- Each query repository documents pagination, indexes, and execution-plan considerations.
- JPA command tests, query integration tests, and three-city E2E remain green.
- Any retained SQL is documented as an escape hatch with rationale.
