# OpenDelivery Product Version and Iteration Roadmap

## 1. Current Status

| Version/iteration | Status | Result |
|---|---|---|
| I00 Domain Baseline | Complete | domain, states, schema baseline |
| I01 Persistence Foundation | Complete | MySQL/JDBC, Flyway, basic ingestion/driver/POD/Outbox and real-MySQL E2E |
| `0.2 Foundation` | Complete | persistent demonstration, not operable production |
| I02–I06 backend | Complete | routing, identity, inbound, dispatch handover, failure return; Web/automation remain |
| I07–I08 | In progress | Cases/callback, closeout, and three-city five-day pilot |
| I09–I15 | Paused | Reprioritize only after the `0.5` gate |

## 2. Boundary

Versions are operating outcomes; iterations are normally one-week vertical slices. The first target is one deployment serving multiple cities with exactly one station each, system-owned routing, and independent station closure. Organization hierarchy, multiple stations per city, inter-station transfer, multi-timezone closeout, and headquarters views are excluded.

## 3. Versions

| Version | Outcome | Scope | Evidence |
|---|---|---|---|
| `0.2 Foundation` | Persistence base | MySQL, Flyway, entities, idempotency, Outbox, E2E | Complete |
| `0.5 Multi-City MOV` | Real operation in multiple cities | routing, Operations Web/RBAC, inbound, dispatch, handover, return, cases, callback, per-station close | 3+ cities for 5 days |
| `1.0 Stable Operations` | Reliably serve initial real partners | adapters, changes/cancel, security, SLO/capacity, backup/recovery | contract, load, DR, security gates |
| `1.1 Operational Efficiency` | Lower manual station cost | bulk work, basic sequencing, notifications, SLA board, metrics | measured improvement |
| `2.0` | No committed scope | planned only after `1.1` evidence | new PRD/design review |

## 4. `0.5 Multi-City MOV`: I02–I08 (Committed Plan)

| Iteration | Demonstrable outcome | Scope | Done evidence |
|---|---|---|---|
| I02 Routing foundation | Address-only upstream routes three cities | station city, one-city constraint, service area, Routing Service, Waybill result, Case, station switch | success/no/ambiguous/manual/cross-station E2E |
| I03 Identity/readiness | Authorized default station; admin can switch | operator tokens, roles, station, audit, readiness, Web shell | permission/cache tests |
| I04 Inbound | Independent manifest receipt/closure | search, scan, discrepancy, Case, close, custody | three-station normal/missing/extra/wrong |
| I05 Dispatch/handover | Publish and transfer driver custody | drafts/publish/cancel, gates, locks, LOAD approval | no cross-station/duplicate assignment |
| I06 Failure/return | Failed parcel returns to original station | reasons, retry limits, RETURN, closeout, same-station reschedule | custody equation |
| I07 Cases/callback | Owned exceptions and recoverable dead letters | Case SLA, Outbox/attempt/ACK, replay, audit | recovery/idempotency/station queues |
| I08 Closeout/pilot | Every city signs independently | reconciliation, drill-down, carryover, runbook/training | 3+ cities, 5 days, zero P0 |

## 5. `1.0 Stable Operations`: I09–I12 (Candidate)

| Iteration | Goal | Deliverables |
|---|---|---|
| I09 Partner Adapter | Second heterogeneous real upstream | SPI, credentials/HMAC, mappings, sandbox, replay |
| I10 Changes/cancel | Safe address/service/cancel updates | version/order handling, lifecycle gates, intercept cases, callback contract |
| I11 Observability/capacity | Quantified service capability | metrics/alerts/traces, slow queries, load test, Outbox fairness, SLO report |
| I12 Security/recovery release | Stable production gates | security test, key rotation, POD retention, restore, migration/rollback drill |

Re-estimate after MOV pilot using real partner fixtures, peak volume, and recovery objectives.

## 6. `1.1 Operational Efficiency`: I13–I15 (Directional)

| Iteration | Goal | Deliverables |
|---|---|---|
| I13 Bulk efficiency | Reduce repeated operator work | bulk discrepancies/waves/cases, safe gates, performance |
| I14 Sequence/notifications | Improve driver/recipient experience | basic stop sequence, dispatch/arrival/failure notifications, templates/audit |
| I15 SLA/metrics | Improve from evidence | SLA board, first-attempt rate, backlog, callback delay, exports/baseline |

No automatic promise of optimization, live tracking, or headquarters aggregation.

## 7. Definition of Done

Each iteration includes bilingual requirement/design, Flyway if needed, backend and Web, authorization/idempotency/concurrency, unit/integration/API/Playwright/real-MySQL E2E, demonstration evidence, release/rollback, and summary. Compilation or tests alone do not establish business acceptance.

`0.5` blockers are routing error, cross-station mixing, broken custody, duplicate assignment, lost POD, unrecoverable callback, or unexplained closeout. Optimization, live tracking, and advanced reporting do not block MOV.
## R02.1 Operations Control Tower (New Mandatory Gate)

R02.1 is inserted after R02 map planning and before R03 physical arrival. It delivers journey navigation, the Today control tower, actionable metrics/exceptions, a next-action queue, and map observability. Capabilities operators cannot discover, understand, or sequence still block a pilot. R02.1 runs as CT01–CT04; see the [Control Tower iteration plan](../iterations/iteration-r02-1-control-tower.en.md).
