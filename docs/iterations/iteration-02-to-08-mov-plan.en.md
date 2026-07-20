# Iterations 02–08: Multi-City Minimum Operable Version

## I02: Multi-City Station Routing Foundation

**Goal:** address/service-only upstream shipments deterministically reach the correct city station. Deliver station city/province/country, one-city constraint, `station_service_area`, normalization, `ShipmentRoutingService`, Waybill current result, routing Cases, and admin station selector. Code owns matching/gates; algorithm internals are not persisted. Done: three-city exact/longest-prefix/city, no match, ambiguity, manual override, address update, and cross-station E2E; migrate required `targetStationCode` to optional hint.

## I03: Operator Identity, Station Context, and Readiness

**Goal:** ordinary users enter a default station; admins switch without cache leakage. Deliver operators/roles/default station, tokens, authorization, audit, station list, readiness, and Web shell. Done: role/action matrix, ordinary cross-station 403, admin switch, Query cache clearing, no secret logs.

## I04: Multi-City Inbound Closure

**Goal:** each station receives routed manifests. Deliver manifest list/detail, idempotent scan, discrepancy Cases/decisions, close, custody, and Web scanner. Done: three-station normal/missing/extra/wrong/duplicate/concurrent-close; cross-station receipt fails; counts recompute.

## I05: Dispatch and Load Handover

**Goal:** station inventory moves into its driver's custody. Deliver candidate inventory, wave draft, publish/cancel gates, locks, LOAD session, submit, approval, and custody. Done: foreign parcel/driver rejected, no concurrent duplicate, no unapproved departure.

## I06: Failure, Return, and Same-Station Reschedule

**Goal:** undelivered parcels return to the original station or have approved next action. Deliver reasons, retry limits, RETURN, station acceptance, closeout, and same-station reschedule. Cross-city address change creates a Case, never automatic transfer. Done: custody equation; foreign return, reasonless failure, and over-limit retry rejected.

## I07: Exceptions and Upstream Callback

**Goal:** every non-automatic result has ownership and every notification can recover. Deliver station-scoped Case workflow/SLA and Outbox/attempt/ACK search, replay, and audit. Done: timeout, 5xx, rejection, restart, duplicate replay; no mixed station queues or duplicate domain transitions.

## I08: Per-Station Closeout and Pilot

**Goal:** each city station independently explains/signs the day. Deliver station reconciliation, drill-down, carryover, sign-off, next-day work, runbook, and training. Run at least three cities for five business days. Done: zero P0; one variance does not block other stations; every variance has a Case; `0.5` release/rollback decision.

## Synchronized Web Delivery

| Iteration | Operations Web | Joint demo |
|---|---|---|
| I02 | station/service area/routing exception | route three cities without upstream station |
| I03 | login/shell/permissions/dashboard/users | default station and safe admin switch |
| I04 | manifest/scanner/discrepancy | receive and close three stations |
| I05 | inventory/wave/load approval | station publication/handover |
| I06 | tasks/failure/return | return and reschedule at origin |
| I07 | cases/ingestion/callback replay | recover overdue/dead letter |
| I08 | balance/drill/sign-off | three independent closes |

## Cross-Iteration Definition of Done

Every write checks authorization, station consistency, idempotency, version, audit, and stable errors. Every schema change has Flyway/dictionary/index/retention. Every transition has legal rule/event/Outbox/regression. Every page has loading/empty/error/403/409, component tests, and Playwright. Each iteration ends with operations demo, evidence, retrospective, bilingual summary, and rollback; unfinished work is not Done.

