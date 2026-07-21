# T01 Persistence ORM Refactoring Iteration (Arrival-Domain Pilot)

> Status: `REVIEWED` (2026-07-21, user authorized direct planning and delivery); owner: shared platform (both product backends); technical refactoring with zero business-behavior change.

## Background

19 classes and 57 call sites use `JdbcTemplate` with hand-written SQL and there is no entity/repository layer: SQL strings are not type-checkable, entity relationships are implicit, and transaction/locking semantics are scattered across services. The goal is to establish a persistence-layer standard on the mainstream North-American Spring stack and a repeatable migration pattern for the remaining contexts.

## Framework selection

| Candidate | Strengths | Problems for this codebase |
|---|---|---|
| Spring Data JPA (Hibernate) | North-American enterprise standard; repository abstraction; `@Version` optimistic and `@Lock` pessimistic locking; entity lifecycle | Weak at MySQL-dialect upserts (`INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`), spatial functions (`ST_*`), and set-based `INSERT…SELECT`; needs a native-SQL fallback |
| jOOQ | Type-safe SQL, dialect fidelity, cheap incremental adoption | Not an ORM mental model; code generation complicates the build; does not answer the "missing entity/repository layer" complaint |
| MyBatis | SQL control | Low adoption in North-American teams |

**Decision**: Spring Data JPA for the command side (entity lifecycle); set-based `INSERT…SELECT`, upserts, spatial functions, and reporting queries stay on `JdbcTemplate`/native SQL as a documented escape hatch. Rationale: the complaint is a missing ORM layer, which is exactly JPA's strength; the combination is the common North-American enterprise architecture; and it changes the existing transaction/locking/idempotency semantics the least.

## Scope

1. Add `spring-boot-starter-data-jpa`; `ddl-auto=none` (Flyway is the only schema source), `open-in-view=false`.
2. Arrival-domain entities and repositories: `ArrivalTrip`, `HandlingUnit`, `HandlingUnitParcel` (composite key) with Spring Data repositories; `version` columns mapped to `@Version`; database-managed columns (`created_at/updated_at`) mapped read-only.
3. `PhysicalArrivalService` refactor: entity CRUD and state moves go through repositories (pessimistic reads, optimistic writes); the `detail`/`trips` reporting queries and set-based `INSERT…SELECT` (default-unit generation, upstream auto-link, area fill) stay on `JdbcTemplate` — the first documented escape-hatch cases.
4. Behavior invariants: API, transaction boundaries, auditing, idempotency, and error codes unchanged; `scripts/arrival-batch-e2e.sh` passes unchanged; the existing 44 unit tests pass.
5. Migration playbook: entity naming, `@Version`/`@Lock` usage, escape-hatch rules, and per-context migration steps for T02–T04 (see [persistence architecture decision](../../design/persistence-architecture.md)).

## Non-goals

- No migration of other contexts (integration, operations core, common identity/delivery).
- No Testcontainers/H2 database-level Java tests (evaluated in T02); no Flyway or API changes.
- No read/write splitting or CQRS; no business rules moved into entities (anemic-by-design this round).

## Wave roadmap

- **T01**: arrival-domain pilot (this iteration).
- **T02**: integration (`ShipmentIngestionService`, `ShipmentRoutingService`, `OutboxDispatcher`).
- **T03**: operations core (inbound, dispatch, planning, areas, control tower, failed returns).
- **T04**: identity and common (operator session/user, auth interceptor, token store, driver repository, delivery operations).

## Risks

- Mixed JPA/`JdbcTemplate` transactions: both share the same `DataSource` and transaction manager, so escape-hatch SQL sees the same transaction; the playbook explicitly forbids crossing data sources.
- Entity-mapping drift: `ddl-auto=none` + explicit `@Column` names + real-MySQL E2E as the safety net.
- Lazy loading and N+1: no association navigation between entities — reference by id only.

## Tests and DoD

- Full `mvn` test run passes; `DB_PASSWORD=… OPS_PASSWORD=… scripts/arrival-batch-e2e.sh` passes unchanged (behavior invariant).
- Docs: this iteration doc, the persistence ADR, and `AGENTS.md` data-access conventions updated together.
- Execution summary records before/after comparison, the escape-hatch list, and T02 inputs.
