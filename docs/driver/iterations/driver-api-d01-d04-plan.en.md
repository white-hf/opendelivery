# Driver API D01-D04 Database Closure Plan

> Document status: `DRAFT`. Product, Driver API, Android, and Operations O05/O06 contract review is required before feature implementation.

## Execution rule

PRD, API, state machine, migration, and test plans are reviewed before implementation. The review decision is recorded here; coding does not start before `REVIEWED`. The old App contract remains compatible while canonical V1 runs in parallel and is later deprecated through measured adoption and release notice.

| Iteration | Existing baseline | Required delivery | Acceptance gate | Status |
|---|---|---|---|---|
| D01 Own tasks | JDBC flat expected/delivery lists | `/driver/v1/tasks` summary/detail/expected list; explicit taskId; ownership/state/version gates | multi-task day, other/revoked tasks, locales, pagination | PLANNED |
| D02 Own scan | persisted LOAD session/events | canonical session API, five outcomes, damage, device idempotency, submit snapshot, offline resume | expected/wrong/unknown/duplicate/damaged; immutable after submit; custody unchanged | PLANNED |
| D03 Delivery/POD | attempts, failure rules, local file store | canonical worklist/attempt/POD contract, evidence gates, retry, offline event, storage abstraction | required photo/note, hash dedupe, ownership, concurrency, compensation | PLANNED |
| D04 Return | RETURN session and Operations approval | expected return list, scan/submit snapshot, missing, reject/reopen, closeout | driver submit leaves custody; O06 approval conserves custody; duplicate/out-of-order idempotency | PLANNED |

## First review sequence

1. Freeze Android request/response samples as compatibility tests.
2. Review D01/D02 API and state machine, including whether `LOADED` remains a pre-approval task-item projection.
3. Review a V13 migration for scan outcomes, submit snapshots, versions, and indexes; never edit applied V1-V12.
4. Review unit, MySQL integration, simulated-App, and O05 joint-E2E fixtures.
5. Mark D01/D02 `REVIEWED` before coding; summarize and commit each vertical slice independently.

## Definition of Done

Compilation or legacy-client smoke is insufficient. Each iteration requires bilingual contracts, upgrade/rollback notes, success/failure states, 401/403/409, idempotency/concurrency tests, localization tests, real-MySQL evidence, and an execution summary.
