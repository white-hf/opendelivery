# Persistence Architecture Decision (ADR): Spring Data JPA + Documented Escape Hatch

> Status: CURRENT (effective from T01); scope: all backend modules; related iteration: [T01 persistence ORM refactoring](../shared/iterations/iteration-t01-persistence-orm.md).

## Decision

Data access is split into two layers:

1. **Command side = Spring Data JPA**. Entity create/read/state transitions/delete go through Spring Data repositories; no SQL strings.
2. **Escape hatch = JdbcTemplate/native SQL**. Only for four cases, each with a code comment stating the reason:
   - set-based `INSERT…SELECT` (e.g. default-unit generation, upstream auto-link, area fill);
   - MySQL-dialect upserts (`INSERT IGNORE`, `ON DUPLICATE KEY UPDATE`);
   - spatial functions (`ST_*`) and vendor-specific syntax;
   - reporting/aggregate read models (e.g. arrival coverage `detail`, control-tower snapshot).

Never loop row-by-row issuing single INSERT/UPDATE statements for set work — use one set-based statement; and never scatter dialect SQL into `@Query` annotations just to stay "pure JPA".

## Hard rules

- **Flyway is the only schema source**: `spring.jpa.hibernate.ddl-auto=none`; entities map existing tables only, with explicit `@Column` names.
- **No association navigation between entities**: reference by id (e.g. `HandlingUnit.tripId`) to avoid lazy loading, N+1, and session-boundary issues; integrity stays with database FKs.
- **Concurrency**: `version` columns map to `@Version` (optimistic); locked reads use repository methods with `@Lock(PESSIMISTIC_WRITE)` instead of `SELECT … FOR UPDATE`.
- **Database-managed columns** (`created_at`/`updated_at` defaults and `ON UPDATE`, generated columns) map as `insertable=false, updatable=false`.
- **Transactions**: `@Transactional` only on public service methods; JPA and JdbcTemplate share one `DataSource` and transaction manager, so escape-hatch SQL and JPA operations in the same transaction see each other.
- **Composite keys** (e.g. `handling_unit_parcel`) map with `@IdClass`; a join table becomes an entity only when it carries its own attributes (`link_source`).
- **No behavior change**: a refactoring iteration proves its behavior invariant by keeping the existing E2E scripts and unit tests green.

## Migration steps (applied per context)

1. Create entities for the context's tables (named `XxxEntity`, package under the domain, e.g. `<context>.<subdomain>.persistence`; T01 arrival entities are located under `operations.arrival.persistence`).
2. Create repository interfaces; derived queries for simple reads; `@Lock` methods for locked reads.
3. Re-point the service at the repositories; rewrite entity CRUD/state moves; keep dialect/set-based/report SQL with an escape-hatch comment.
4. Run the context's existing unit tests and E2E scripts; confirm behavior is unchanged.
5. Record migrated classes, the escape-hatch list, and leftovers in the execution summary.

## Why not jOOQ / MyBatis / pure JPA

- Pure JPA cannot cleanly express upserts, spatial functions, and set-based `INSERT…SELECT`; forcing them into native `@Query` strings is messier.
- jOOQ is type-safe and dialect-faithful, but code generation complicates the build and does not answer the missing entity/repository layer.
- MyBatis has low adoption in North-American teams, which hurts hiring and collaboration.
