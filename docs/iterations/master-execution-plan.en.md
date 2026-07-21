# OpenDelivery Version and Iteration Master Execution Plan

> **Rebaselined:** From 2026-07-21, driver execution and Operations are separate product streams. Future priority, numbering, and joint gates follow the [Two-Product Execution Plan](two-product-execution-plan.en.md); former I07-I15 sequencing remains historical.

## 1. Purpose, Status, and Assumptions

This turns the [roadmap](../roadmap/version-roadmap.en.md) into execution. I00–I01 are complete. I02–I06 backend, database, and real-MySQL primary flows are delivered, but Operations Web and systematic automation remain unfinished DoD. From 2026-07-20, delivery is pilot-first: finish the operator UI, I07, I08, and automated gates required for five consecutive business days in three cities. I09–I15 are paused until the `0.5` gate.

The [Three-City Five-Day Pilot-First Plan](pilot-first-execution-plan.en.md) is now the critical-path authority; later versions are post-gate backlog.

The calendar assumes a 2026-07-20 start and one cross-functional delivery unit. Normal iterations are five working days; I08 is ten days to include a five-day pilot. Dates are baselines, never permission to bypass quality. Scope change, missing external inputs, or P0 defects trigger replanning.

## 2. Required Inputs

| Due | Input | Use | Missing outcome |
|---|---|---|---|
| Before I02 | 3+ cities/stations, common business timezone, postal/city coverage | routing truth | block I02 |
| Before I02 | Canonical normal/missing/duplicate/conflict fixtures | integration tests | block I02 |
| Before I03 | roles, default stations, high-risk action matrix | authorization | block I03 |
| Before I04 | manifest normal/missing/extra/wrong/damage samples | inbound acceptance | block acceptance |
| Before I06 | failure reasons, retry and POD policy | delivery rules | block I06 |
| Before I07 | callback sandbox and ACK/error fixtures | recovery | block I07 |
| Before I08 | three-city operators/drivers/data/on-call/rollback window | pilot | block I08 |
| Before I09 | second heterogeneous partner specification/sandbox | adapter | block `1.0` |
| Before I11 | average/peak volume and RTO/RPO | capacity/recovery | block `1.0` gate |

Missing business rules are documented blockers, never temporary hard-coded assumptions.

## 3. Weekly Cadence

| Time | Activity | Required artifact |
|---|---|---|
| Prior Friday/Monday AM | refinement/Ready | stories, examples, non-goals, API/schema delta, acceptance tests |
| Monday PM | design/task slicing | bilingual docs, migration draft, contract, risks |
| Tue–Wed | vertical implementation | DB→repository→service→API→Web plus primary failures |
| Thursday | integration/non-functional | MySQL E2E, auth, idempotency, concurrency, Playwright, migration dry-run |
| Friday AM | regression/release candidate | full tests/build/Docker/rollback |
| Friday PM | operations demo/acceptance/retro | evidence, defects, summary, next-plan adjustment |

An unfinished story returns to backlog; code-complete and later-testing are not separate Done states.

## 4. Calendar and Gates

| Date | Iteration/gate | Outcome |
|---|---|---|
| Jul 20–24 | I02 | three-city station routing |
| Jul 27–31 | I03 | operator identity/context/Web shell |
| Aug 3–7 | I04 | multi-city inbound closure |
| Aug 10–14 | I05 | dispatch/load custody |
| Aug 17–21 | I06 | failure/return/reschedule |
| Aug 24–28 | I07 | cases/callback recovery |
| Aug 31–Sep 11 | I08 | closeout/five-day pilot |
| Sep 14 | `0.5` gate | release, limited extension, or rollback |
| Sep 14–Oct 9 | I09–I12 | `1.0` candidate, gate-approved only |
| Oct 12 | `1.0` gate | stable-production decision |
| Oct 12–30 | I13–I15 | `1.1`, metrics-approved only |
| Nov 2 | `1.1` gate | measured-efficiency release decision |

Reserve roughly 20% capacity for unknowns, not advance scope.

## 5. `0.5 Multi-City MOV`

### I02: Routing (Jul 20–24)

Add station city/province/country, one-city constraint, `station_service_area`, Waybill current routing result/indexes and backfill. Implement normalization, deterministic Routing Service, no/ambiguous/manual result, and optional upstream hint. Scaffold Web station/service-area/routing-exception pages. Verify three-city success, no/ambiguous, duplicate/same-key-different-body, manual override, update, inactive station, empty/upgrade migration. Exit: zero routing/cross-station errors and no required upstream station.

### I03: Identity and Readiness (Jul 27–31)

Add operators, roles/default station, sessions, audit. Implement operations token lifecycle, `/me`, station list, guards, readiness, and Ops-key migration switch. Build login/shell/routes/context/dashboard/users and clear Query cache on station switch. Verify authorization matrix, 401/403, foreign IDs, rotation/revocation, cache isolation, redaction. Exit: ordinary users cannot operate another station and high-risk actions are auditable.

### I04: Inbound (Aug 3–7)

Deliver manifest paging/detail/start/scan/discrepancy/close, idempotent facts, Case linkage, custody, and Web scanner. Verify three stations, normal/missing/extra/wrong/damaged/unknown/duplicate, concurrent close, foreign receipt, and count reconstruction. Exit: missing inventory is unavailable and every discrepancy is resolved or Case-backed.

### I05: Dispatch and Handover (Aug 10–14)

Deliver dispatch candidates, wave drafts/items/publish/cancel, short locks/version, all station/routing/custody/case/assignment/driver gates, Driver LOAD submit, and supervisor approval UI. Verify concurrent dispatchers, duplicate/foreign parcel/driver, unapproved discrepancy, rollback, and own-task access. Exit: one active assignment maximum and custody moves only on approval.

### I06: Failure and Return (Aug 17–21)

Deliver reason/evidence policy, attempt limit, Driver V2 attempt, task closeout, RETURN session/approval, and same-station reschedule. Cross-city address change creates a Case. Verify success/POD, unavailable/refusal/address, idempotency, retry limit, offline replay, foreign return, partial sync, and custody equation. Exit: no unexplained driver holding.

### I07: Cases and Callback (Aug 24–28)

Deliver station Case lifecycle/SLA and Outbox/attempt/ACK search, dead-letter replay, partner/station filter, rejection Case, and Web timeline/monitoring. Verify timeout, 429, 5xx, permanent 4xx, business rejection, crash/restart/lease, duplicate replay, and redaction. Exit: every exception has owner/SLA and every callback is recoverable or explicitly waived.

### I08: Closeout and Pilot (Aug 31–Sep 11)

Week one builds per-station balance, drill-down, recompute, Case carryover, sign-off, next-day tasks, runbook, training, and rollback. Week two runs three cities for five consecutive days through readiness, inbound, dispatch, handover, success/failure, callback, and close. Exit: P0=0, routing/cross-station errors=0, custody/variance explained, callback eventual success ≥99.5%, and joint release decision.

## 6. `1.0 Stable Operations`

Start only after MOV gate plus real second-partner fixtures, peak volume, and RTO/RPO.

- **I09 Partner Adapter (Sep 14–18):** adapter SPI, second inbound/outbound mapping, partner HMAC/key version, quarantine/replay, contract sandbox. Partner differences must not leak into fulfillment.
- **I10 Change/Cancel (Sep 21–25):** external version/order, conflict, address/service/cancel, lifecycle gates, in-transit intercept Case, callback. Test pre-route through terminal-state matrix.
- **I11 Observability/Capacity (Sep 28–Oct 2):** correlated metrics/logs/traces, alerts, slow-query baseline, realistic load, Outbox fairness, p50/p95/p99, validated throughput, bottlenecks, scale threshold, SLO proposal.
- **I12 Security/Recovery/Release (Oct 5–9):** security test, key rotation, POD access/retention, dependency scan, backup restore, migration failure recovery, application rollback, runbook. Gate requires measured RTO/RPO.

## 7. `1.1 Operational Efficiency`

Start only after at least two weeks of stable `1.0` baseline; each item has a measured improvement target.

- **I13 Bulk Operations (Oct 12–16):** bulk discrepancies/waves/cases, unified preflight, atomicity, permission, result export, performance; compare task time/error rate.
- **I14 Sequence/Notification (Oct 19–23):** editable basic stop sequence—not an optimizer—and dispatch/arrival/failure notifications, templates, opt-out/failure/audit; enable one station first.
- **I15 SLA/Metrics (Oct 26–30):** SLA, first-attempt rate, backlog, closeout, callback-delay boards and controlled export; publish only with improvement over MOV/1.0 baseline.

## 8. Test, Branch, and Release Policy

Use short-lived branches and forward-only Flyway migrations. Backend gates: Maven unit/integration/API, real-MySQL E2E, empty/upgrade migration, Docker. Frontend gates: lint/typecheck/Vitest/RTL/build/Chromium Playwright; version gates add three-browser smoke. Every write tests 401/403/404/409, idempotency, version conflict, and cross-station behavior. Release uses expand/migrate/contract. P0 stops release; P1 needs owner/version; P2 enters ranked backlog.

## 9. Reporting and Change Control

Board states are `Ready → In Progress → Review → Verified → Accepted`. Weekly reporting contains accepted outcomes, evidence, residual risks, and next critical path. New scope is tested against the current release goal; non-blocking work moves to later backlog. Date changes update this plan, roadmap, and both languages together.
