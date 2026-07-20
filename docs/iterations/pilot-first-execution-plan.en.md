# Three-City Five-Day Pilot-First Execution Plan

## Goal and Boundary

The only active goal is enabling YHZ, YYZ, and YVR to operate five consecutive business days through readiness, intake, inbound, dispatch, handover, delivery/failure return, Cases, callbacks, and signed closeout. Second Partner, bulk efficiency, notifications, and advanced metrics are deferred.

## Priority Iterations

| Order | Iteration | Operable outcome | Evidence |
|---|---|---|---|
| P0-A | Minimum Operations Web | login/switch, dashboard, Manifest, dispatch, Case, callback, closeout | Vitest + Playwright; three-role browser demo |
| P0-B | I07 Case/callback | owner/SLA and searchable, explainable, replayable Outbox | automated 429/5xx/permanent-4xx/timeout/replay |
| P0-C | I08 closeout | inventory, driver custody, exception, callback variance recompute/carry/sign | daily three-station fixtures |
| P0-D | Automation gate | one command provisions and runs a full business day in three stations | Maven, real MySQL, Playwright, upgrade migration |
| P0-E | Five-day pack | runbook, accounts, on-call, rollback, daily report, defect template | five days of human sign-off |

## 2026-07-20 Execution Snapshot

- P0-A is in progress: the React 19/TypeScript/Vite/Ant Design console provides login, role-aware navigation, admin station switching, readiness, Manifest start/scan/close, dispatch candidate/draft/publish, and Case list. Callback and closeout screens await P0-B/P0-C APIs.
- Unit evidence: all 13 Maven reactor tests and 8 Vitest tests pass; strict frontend typecheck and production build pass.
- Real MySQL: Flyway V1-V7 validates; all 18 authenticated read-model requests (six per station) pass across three stations.
- `scripts/db/003_three_station_pilot_seed.sql` idempotently provisions non-production station staffing and service areas. Seeded credentials must be rotated after use.
- Remaining P0-A work: browser Playwright flows, Manifest discrepancy decisions, load approval UI, and the callback/closeout modules.

## Test Priority

- Unit tests first cover routing selection, state gates, authorization matrix, count recomputation, failure rules, Case state machine, callback backoff, and closeout invariants. New business logic ships with unit tests.
- Per-station E2E covers normal, missing, extra, damaged, failed redispatch, upstream return, and address Case.
- Access covers missing token, wrong role, cross-station URL/body/header, and foreign driver task.
- Consistency covers duplicate upstream/device events, concurrent publish/close, token rotation, and active-task uniqueness.
- Closeout invariant: `opening + inbound - delivered - upstream return = station + driver custody + explained variance`.

## Gates

System readiness requires green unit/integration/E2E, P0=0, zero cross-station errors, explained variance, and callback-test eventual success ≥99.5%. The real five-day gate additionally requires operators, drivers, data, and daily sign-off in all three cities; simulation cannot replace external evidence.
