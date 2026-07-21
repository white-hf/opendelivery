# OpenDelivery Operations Web Product and Interaction Specification

## 1. Goal and Scope

Operations Web serves a multi-city, one-station-per-city MOV so each station can complete receiving, dispatch, handover, delivery monitoring, exceptions, callback recovery, and closeout without SQL or developer intervention. It calls `/ops/v1/**` and never accesses the database directly.

MOV targets desktop (minimum 1366×768) and common landscape tablets. Mobile operations, customer portal, route map, live GPS, billing, and advanced BI are excluded. Chinese ships first, with i18n keys from day one.

## 2. Information Architecture

```text
Login
├── Daily Dashboard
├── Inbound
│   ├── Routing Exceptions
│   ├── Manifest List
│   └── Manifest Detail / Receipt / Discrepancy
├── Dispatch
│   ├── Dispatchable Inventory
│   ├── Wave List
│   └── Wave Detail / Load Handover
├── In-Transit and Return
│   ├── Driver Tasks
│   └── Return Handover
├── Exception Center
│   ├── Case Queue
│   └── Case Detail
├── Integration Monitoring
│   ├── Ingestion Batches
│   └── Callback / Replay
├── Daily Closeout
└── Administration
    ├── Users and Roles
    ├── Drivers
    ├── City Stations and Service Areas
    └── Partner Read-Only Configuration
```

Navigation hides unauthorized sections, but backend authorization remains mandatory. Station/date remain visible; ordinary users have a fixed default station and admins may switch. Switching replaces every query/cache/write `stationId`; no cross-station aggregate is provided.

## 3. Role/Page Matrix

`R` read, `W` operate, `A` approve/high risk, `—` hidden.

| Capability | Inbound | Dispatcher | Supervisor | Exception | Integration | Admin |
|---|---:|---:|---:|---:|---:|---:|
| Dashboard | R | R | R | R | R | R |
| Routing exception/override | R | R | A | W | R | R |
| Manifest/receipt | W | R | A | R | — | R |
| Inventory/wave | R | W | A | R | — | R |
| Load/return approval | R | W | A | R | — | R |
| Driver task monitoring | R | W | A | R | — | R |
| Cases | R | R | A | W | R | R |
| Callback/replay | — | — | A | R | W | R |
| Closeout/sign-off | R | R | A | R | R | R |
| User/role | — | — | R | — | — | W |

Discrepancy acceptance, publish/cancel, handover approval, dead-letter replay, and closeout sign-off require an impact summary, second confirmation, and reason.

## 4. Page Specifications

### 4.1 Login and Session (I03)

Credential and password fields; errors never reveal account existence. Route to the default station dashboard. Refresh access silently; refresh failure clears memory and returns to login. A 401 triggers one refresh/replay, 403 shows forbidden without a loop, and logout revokes the session.

### 4.2 Dashboard and Readiness (I03)

Cards: expected, received, dispatchable, dispatched, delivered, failed, driver-held, open cases, and callback dead letters. Readiness shows partner connection, station status, active drivers, prior carryover, and pending manifests. Every number drills into a filtered list. Without push, refresh every 60 seconds and show last-updated time. Red means immediate action, not ordinary counts.

### 4.3 Station and Routing Exceptions (I02)

Admins configure city stations and service areas: country, province, normalized city, postal prefix, priority, and effective dates. Routing exceptions show raw/normalized address summary, failure reason, and candidate station; operators may retry system routing or manually assign with reason. The UI does not expose algorithm branches and cannot change station after receipt.

### 4.4 Manifest and Receipt (I04)

Filters: station, expected date, status, partner, manifest number. Columns: number, partner, expected/actual arrival, expected/received/discrepancy, state. Detail includes summary, focused scanner input, item table, and discrepancy drawer.

Scanner-enter success gives sound/green; duplicate is yellow; wrong/unknown is red and preserves diagnostics. Decisions require reason/note. Close displays gates; unresolved items require linked Cases and supervisor carryover.

### 4.5 Inventory and Waves (I05)

Filters: promised date, route, postal code, service, status. Select current page or filtered results, maximum 500 per operation. Wave needs service date, route, driver. Draft save and publish are separate. Publish confirmation shows driver/count/errors; item failure publishes none and returns copyable/downloadable reasons. Unstarted publication can cancel; started work requires reassignment.

### 4.6 Load, Tasks, and Return (I05–I06)

Task view shows driver, state, expected/scanned/discrepancy, collected/delivered/failed/returned, and last sync. Handover compares expected and scan facts; supervisor can correct, remove, reject, or approve with reason.

RETURN uses the same discrepancy component. Approval selects next-day reschedule, upstream return, or investigation. Custody stays visible so failed is never mistaken for returned.

### 4.7 Exception Center (I07)

Filters: station, type, state, priority, owner, overdue, parcel; default “mine + unassigned.” Columns: case, type, tracking, priority, owner, state, SLA remaining, updated. Detail has unified parcel timeline plus case actions. Claim, transfer, request evidence, decide, resolve, and close follow the state machine. Failed submit keeps input; version conflict shows the latest concurrent action.

### 4.8 Integration Monitoring (I07)

Ingestion shows source, counts, state, duration. Callback shows partner, event, parcel, state, attempts, next retry, error, ACK. Detail displays redacted payload and attempts. Replay is integration/supervisor only, needs reason and creates a new attempt. Never display secrets, tokens, full phone, or full address.

### 4.9 Daily Closeout (I08)

Show inventory equation, actual inventory, variance, driver custody, open tasks, manifest discrepancy, missing POD, open Cases, and unacknowledged callbacks, all drillable. Supervisor recalculates, resolves or links Cases, then signs. Variances require per-item carryover reason and Case. Signed records are read-only; corrections create audited versions.

### 4.10 Administration (I03)

MOV provides operator list/create/disable, roles/station grants, and driver read/enable/disable. Partner/station/secret configuration remains read-only or deployment-managed to avoid premature configuration-center scope. Passwords never display; secrets never reach the browser.

### 4.11 Arrival batch workbench (R03)

The arrival page is organized around the batch work flow: create an arrival batch (leave the batch number blank to auto-generate it, together with about ten default PALLET units) → fill units from areas in the batch detail → review on the map and in the parcel detail → advance the arrival state. The batch list shows vehicle, seal, expected arrival, unit count, and linked/expected pieces.

The batch detail drawer holds: batch fields, the state-advance action, the unit table (label, type, status, declared/linked/scanned/exception counters, driver count), a "fill from areas" entry (multi-select published areas plus reason, executed after map review), and the supplemental-unit drawer (typed tracking numbers are an occasional supplement only). Selecting a unit row drills into its parcels (tracking no, piece status, link source, task, driver); parcels declared upstream but not linked appear as a warning strip. Any disagreement between a counter and the parcel-detail rollup raises an error — aggregate always equals detail is the page acceptance gate.

## 5. Common Interaction Rules

- Server-side filter/sort/cursor pagination; URL preserves filters.
- Skeleton on first load; distinguish no records from no filter results; background refresh keeps existing rows.
- Disable repeat mutation; send idempotency and version; invalidate only affected queries.
- Field validation is inline; conflicts give actionable next steps; unknown errors show request ID.
- High-risk buttons use explicit verbs and impact, not generic “OK.”
- Display station local time with UTC detail; show zero values.
- Keyboard access, visible focus, text/icon plus color; baseline WCAG 2.1 AA.
- PII masked by default; authorized reveal is audited.

## 6. States and Acceptance

Every page tests loading, empty, normal, partial, validation, 401, 403, 404, 409, 429, and network/500. Scanning adds device duplicate/offline recovery; lists demonstrate 10,000+ server-paginated records; batch validation may partially identify errors but the transaction fails atomically.

MOV acceptance: a new operator with two hours of training completes readiness, receipt, dispatch, handover, exception, callback replay, and closeout without developers/database access; five-day pilot has zero P0 interaction defects.
