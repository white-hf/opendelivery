# MOV Mandatory Capability Solutions

This document converts required capabilities into implementable, testable features for a multi-city MOV with exactly one independent station per city.

## 0. Address Normalization, Station Routing, and Multi-City Context (P0)

Upstreams provide address and service requirements, not internal stations. Add a `shipment-routing` logic module: code owns normalization, matching, priority, conflicts, and reroute gates; `station_service_area` stores configurable coverage only; Waybill stores only current `routing_status/resolved_station_id/routing_reason_code/routed_at`. No/ambiguous match creates a Case; manual choice uses general audit. Operations queries carry station context and every fulfillment command validates parcel/manifest/wave/task/driver station consistency. No automatic station change after receipt and no inter-station transfer in MOV.

**Acceptance:** at least three cities with one station each; deterministic routing without upstream station; no/ambiguous match enters exception; cross-station receive/dispatch/handover/return is rejected; each station closes independently.

## 1. Operator IAM and Audit (P0)

Add operator users, roles, station grants, and immutable audit. MOV roles are `INBOUND_OPERATOR`, `DISPATCHER`, `SUPERVISOR`, `EXCEPTION_AGENT`, `INTEGRATION_OPERATOR`, and `ADMIN`. Short-lived access tokens and rotating refresh tokens are separate from driver identity. Authorization checks role, action, and station. Publish, reassignment, discrepancy acceptance, replay, and closeout capture before/after, actor, request ID, time, and reason.

**Acceptance:** no cross-station access; supervisor actions searchable by object/date; the global operations API key remains migration-only.

## 2. Inbound and Discrepancy Workbench (P0)

Provide expected manifests, live receipt counts, and discrepancy queues. Classify scans as `RECEIVED/MISSING/EXTRA/WRONG_STATION/DAMAGED/UNKNOWN`. Supervisors can await data, quarantine, redirect, or accept with reason; close recomputes all counts.

**Acceptance:** missing pieces never enter inventory; duplicate device events do not increment counts; every discrepancy has a decision trail.

## 3. Dispatch Gates (P0)

Waves start as `DRAFT`; publish is a separate command. Publication locks and validates parcels for station, lifecycle, custody, active assignment, and driver eligibility. Post-publication changes use explicit cancel/reassign events.

**Acceptance:** concurrent publication cannot double-assign; failures are itemized; started tasks cannot be silently overwritten.

## 4. Bilateral Handover and Return (P0)

LOAD/RETURN sessions follow `OPEN → SUBMITTED → APPROVED/REJECTED`. Approval atomically updates item/parcel projections and writes custody/status events. RETURN approval selects `RESCHEDULED`, `RETURN_PENDING`, or investigation.

**Acceptance:** custody never changes before approval; collected equals delivered + returned + explicitly approved driver-held; every transfer has both sides and a timestamp.

## 5. Failed Delivery and Cases (P0)

Minimum reasons: unavailable recipient, bad address, refusal, inaccessible location, damage, cancellation, other. Rules map to next-day retry, station return, or manual review. High-risk outcomes create station-queued cases with SLA. Actions include claim, note, request evidence, decide, resolve, and close.

**Acceptance:** no reasonless failure; drivers cannot exceed retry limit; overdue cases are visible.

## 6. Callback Compensation (P0)

Expose pending, retry, dead-letter, and acknowledged events. Replay creates a new attempt and preserves history; the domain event keeps a stable external idempotency ID. Separate transport receipt from business acceptance; business rejection creates a case.

**Acceptance:** restart loses no event; authorized replay works; duplicate callback does not duplicate upstream state.

## 7. Daily Closeout (P0)

Calculate station/date inventory balance plus driver custody, open tasks, manifest discrepancies, POD gaps, cases, and unacknowledged callbacks. Zero variances auto-pass; otherwise the supervisor links cases or supplies carryover reasons before sign-off.

**Acceptance:** history is immutable; one final station/date record; carryovers appear next day.

## 8. Partner Adapter (P1, after MOV)

Define `supports/parseInbound/validate/mapOutbound`; persist raw input before converting to Canonical Shipment. Use versioned configuration for simple fields/enums and code plus contract tests for complex behavior. MOV implements only `GenericCanonicalJsonAdapter`; build the general framework when onboarding the second real partner.
