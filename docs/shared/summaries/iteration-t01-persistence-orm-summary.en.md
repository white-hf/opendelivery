# T01 Persistence ORM Refactoring Execution Summary

> Completed: 2026-07-21; status: `COMPLETED` (arrival-domain pilot). Related: [iteration doc](../iterations/iteration-t01-persistence-orm.md), [persistence ADR](../../design/persistence-architecture.md).

## Delivered

- **JPA infrastructure**: `spring-boot-starter-data-jpa`; `spring.jpa.hibernate.ddl-auto=none` (Flyway is the only schema source), `open-in-view=false`; the memory profile additionally excludes `HibernateJpaAutoConfiguration`/`JpaRepositoriesAutoConfiguration` (in-memory mode and existing unit tests unaffected).
- **Persistence ADR**: `docs/design/persistence-architecture.md` (bilingual) — JPA on the command side, four escape-hatch categories (set-based `INSERT…SELECT`, dialect upserts, spatial functions, reporting read models), id-only references without association navigation, `@Version`/`@Lock` rules, and the per-context migration playbook.
- **Arrival-domain migration (reference implementation)**: `operations/persistence/` gains `ArrivalTripEntity`/`HandlingUnitEntity` (`@Version`, database-managed columns read-only) and `ArrivalTripRepository`/`HandlingUnitRepository` (`@Lock(PESSIMISTIC_WRITE)` locked reads); all entity CRUD and state moves in `PhysicalArrivalService` now go through the repositories.
- **Conventions landed**: new Data Access & Persistence section in `AGENTS.md`.
- **Behavior invariant**: API, transaction boundaries, auditing, idempotency, and error codes unchanged — proven by the identical E2E script.

## Escape-hatch list (kept on JdbcTemplate, all commented)

`trips()`/`detail()` coverage aggregates; the daily-sequence `DATE()` lookup; default-unit `INSERT IGNORE`; operator-tracking `INSERT IGNORE`; upstream auto-link `INSERT…SELECT`; area-fill `INSERT…SELECT`; area validation COUNT; audit writes (`operation_audit_log` belongs to the operations-core context, migrating in T03). The `HandlingUnitParcel` join table stays unmapped because it is only touched set-wise.

## Verification evidence

- `mvn clean package`: 44 Java tests green (including memory-profile `ApplicationApiTest`), BUILD SUCCESS.
- Real MySQL `opendelivery`: `DB_PASSWORD=… OPS_PASSWORD=… scripts/arrival-batch-e2e.sh` passes **identically** to the pre-migration run on all three stations (behavior invariant).
- Playwright arrival specs (new en/zh workbench + control-tower entry) pass.

## Decisions on record

- `@Version` is now genuinely enforced: the JDBC code never used the `version` column for concurrency; per the data model's intent, state moves now serialize on the pessimistic lock and write back with an optimistic check.
- No association navigation between entities (`HandlingUnit.tripId` by id) — no lazy loading or N+1.
- Status fields stay String-mapped (database CHECK is the authority) to avoid mapping drift.

## T02 inputs

Next context: integration (`ShipmentIngestionService`, `ShipmentRoutingService`, `OutboxDispatcher` — dense `ON DUPLICATE KEY UPDATE` territory, a real coverage test of the escape-hatch rules); evaluate Testcontainers-based repository tests; follow the ADR playbook.

## Rollback

Pure refactoring with no schema/API change; reverting means restoring `PhysicalArrivalService` and the wiring — the JPA dependency and settings can stay harmlessly.
