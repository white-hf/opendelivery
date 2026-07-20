# OpenDelivery Last-Mile Driver Platform PRD

> This document remains as early requirements context. Use the [Integrated Product and Operations Specification](product-and-operations-specification.en.md) as the current authority so early targets are not confused with MOV commitments.

## 1. Context and Goal

OpenDelivery is the backend for a last-mile driver application. Shipment master data originates from upstream customers or carriers through API push, scheduled pull, or files. OpenDelivery owns intake validation, station receipt, sort/dispatch, driver handover, delivery execution, POD, exceptions, returns, and acknowledged status callbacks. Every parcel must be traceable from source event to delivery or upstream return.

The original implementation was a demo: `MemoryDataStore` embedded parcels and mutated status directly, without upstream intake, station inventory, formal dispatch, persistence, complete history, or reliable callbacks.

## 2. Actors and Boundaries

- **Upstream systems** own commercial shipment instructions and receive status/POD callbacks.
- **Integration operations** manage connectors, mappings, replay, reconciliation, and dead letters.
- **Station operators and supervisors** receive, sort, dispatch, resolve discrepancies, and sign off the day.
- **Dispatchers** create waves, assign drivers, monitor execution, and reassign work.
- **Drivers** authenticate, scan handovers, deliver, capture POD, fail attempts, and return parcels.
- **Customer service** handles address changes, appointments, refusals, and exception decisions.
- **OpenDelivery** is the source of truth for last-mile execution, custody, and operational events.

## 3. Batch Semantics

Four separate concepts must not be collapsed:

1. **Ingestion batch**: one push request, file, pull page, or connector run.
2. **Inbound manifest**: upstream pre-advice for physical items expected at a station.
3. **Dispatch wave**: an operational group built by service date, zone, route, capacity, and driver.
4. **Scan session**: a driver's load/return/transfer scanning session; it never owns shipment master data.

## 4. Closed-Loop Workflow

1. Persist and authenticate upstream input, enforce event idempotency, validate required fields, and quarantine bad records.
2. Create or version waybills/parcels; route late cancellations or address changes to controlled interception.
3. Reconcile physical arrival against the manifest; classify missing, extra, duplicate, wrong-station, damaged, or unreadable items.
4. Sort and make eligible inventory dispatchable; enforce station, status, capacity, and assignment gates.
5. Publish a driver task and complete bilateral scan handover before custody moves to the driver.
6. Execute delivery with offline-safe idempotent commands. Successful attempts satisfy POD policy; failures require a reason and disposition.
7. Return undelivered items to station custody, reschedule, reassign, or hand them back upstream with evidence.
8. Write every valid status transition and outbox message atomically; retry callbacks until upstream ACK or approved waiver.
9. Close the operating day only after inventory, driver holdings, tasks, POD, exceptions, and callbacks reconcile.

## 5. Parcel Lifecycle

Primary path:

`RECEIVED → AT_STATION → SORTED → READY_FOR_DISPATCH → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED`

Exception paths include failed delivery and reschedule, return to station/upstream, cancellation, loss, damage, and address exception. Scans are immutable facts; only validated domain commands change lifecycle state.

## 6. Functional Scope

Partner/configuration management; push/pull/file ingestion; waybill and multi-piece parcels; manifests and station inventory; zones/routes/waves/tasks; load and return scans; driver sessions; delivery attempts and POD; exceptions and SLA queues; returns; status history/outbox/callback ACK; daily reconciliation; RBAC/audit; and operational metrics.

## 7. Acceptance Rules

- Duplicate upstream events cannot create duplicate shipments.
- A parcel has at most one active task and one current custody owner.
- Handover is required before `OUT_FOR_DELIVERY`.
- State writes use idempotency keys and optimistic versions.
- Terminal records trace back to source payload, custody, task, evidence, events, and upstream ACK/waiver.
- Operators can open, run, close, and hand over a station shift without direct database access.

## 8. Decisions Still Requiring Business Input

Confirm the first upstream contract, multi-piece aggregation, mandatory receipt scanning, tolerated load discrepancies, POD matrix, failure/retry/return SLA, retention, and whether routing is upstream-provided, operator-managed, or optimized later.
