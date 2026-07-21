# T02 Persistence ORM Refactoring Iteration (Integration Context)

> Status: `COMPLETED` (Completed on 2026-07-21); product: Shared Platform (technical refactoring, no behavior change); executed per wave 2 of [Persistence ADR](../../design/persistence-architecture.en.md). Reference: [Execution Summary](../summaries/iteration-t02-persistence-integration-summary.en.md).

## Background

T01 established the "JPA command side + four escape hatches" pattern with the arrival-domain pilot. The integration context (`ShipmentIngestionService`, `ShipmentRoutingService`, `OutboxDispatcher`) has the densest `ON DUPLICATE KEY UPDATE` / dialect SQL, making it the right place to verify escape-hatch coverage and readability under heavy dialect usage.

## Scope

1. **Entities/repositories**: `waybill`, `parcel`, `ingestion_batch`, `ingestion_record`, `upstream_partner`, `station_service_area`, `outbox_event`, `callback_attempt` (per the ADR playbook: `<context>.persistence` package, id-only references, `@Version`, database-managed columns read-only).
2. **Service migration**: entity CRUD of the three services moves to repositories; upserts, `INSERT…SELECT`, and reporting stay on `JdbcTemplate` with escape-hatch comments (this context will show a high escape-hatch share — record the list honestly).
3. **Testcontainers evaluation**: assess the cost/benefit of Testcontainers-MySQL repository tests; record the adopt/skip decision with reasons (if adopted, pilot on integration repositories).
4. **Behavior invariant**: `scripts/mysql-e2e-test.sh` and `scripts/arrival-batch-e2e.sh` pass identically before and after; ingestion contract, routing algorithm, and outbox mechanics unchanged.

## Non-goals

- No changes to the ingestion API contract, routing rules, or outbox retry/lease mechanics.
- No work on operations-core or common identity/delivery contexts (T03/T04).

## Dependencies

- T01 ADR and the arrival reference implementation; V13 schema.

## Migration and compatibility

- No schema change, no Flyway; `ddl-auto=none` stays.

## Risks

- `parcel` is wide and shared across contexts → this iteration maps only the field semantics the integration side reads/writes; cross-context write paths (e.g. delivery state machine) stay on existing JDBC until T03/T04 unify them — record this temporary dual track explicitly in the summary.
- `ON DUPLICATE KEY UPDATE` read-modify-write semantics → keep the original SQL as escape hatches; do not force JPA.

## Tests and DoD

- Full `mvn` unit tests pass; both real-MySQL E2E scripts green (behavior invariant).
- Execution summary: migrated-class list, escape-hatch list, Testcontainers verdict, T03 inputs; update the ADR and `AGENTS.md` as needed.
