# Operations Control Tower and Journey Navigation

## Product Outcome

Within one minute, a new operator must know today's volume, whether capacity is sufficient, the current stage, active blockers, and where to act next. The station business day is the global context: navigation communicates the journey, Today supports decisions, and workspaces execute them. The first release supports one station in YHZ, YYZ, and YVR; headquarters views, inter-station transfer, and automatic route optimization are out of scope.

## Users and Work Model

| Role | Primary concern | Frequent action |
|---|---|---|
| Dispatcher | Distribution, capacity gaps, unassigned parcels | Set shifts, create batches, assign/split, freeze and publish |
| Inbound operator | Trips/units and scan differences | Register arrival, monitor progress, handle wrong/damaged items |
| Supervisor | Blockers, custody risk, release/close readiness | Approve, reassign, resolve cases, sign carryover |

Changing station or service date must change metrics, map, lists, work queue, and cached data together.

## Journey Navigation

Primary navigation follows the operating day and shows work/blocker badges:

1. **Today** — control tower, stage progress, next actions.
2. **Order readiness** — ingestion, routing, geocode, area match, data exceptions.
3. **Dispatch planning** — map, shifts/capacity, batches, assignment, freeze, publish.
4. **Inbound arrival** — trips, handling units, physical arrival and condition.
5. **Driver scan** — own expected list, progress, missing/wrong/damaged observations.
6. **Handover approval** — reconciliation, exception closure, custody release.
7. **Delivery monitoring** — active delivery, delivered, failed, retry and return.
8. **Day close** — custody conservation, open work, carryover and sign-off.
9. **Configuration** — areas, drivers, stations and access, separated from daily work.

Stages derive from facts as `NOT_STARTED/IN_PROGRESS/BLOCKED/COMPLETED`; users cannot edit them. Selecting a stage opens its workspace with station, date, and blocker filters.

## Today Control Tower

The header shows station name/code, service date, local time, data freshness, and source health. Technical values such as `stationId=3` and `ready=true` become operator language and specific readiness reasons.

The journey bar covers order readiness through closeout with completion, work/blocker counts, and drill-down. Metrics cover expected, routed, area-matched, assigned, arrived, valid-scanned, released, out-for-delivery, delivered, and failed/returned. Capacity shows available drivers, total/used/remaining capacity and shortfall. Exceptions cover unrouted, missing geocode, unmatched area, over-capacity, unsubmitted scans, damaged parcels, awaiting approval, and open cases. Every number drills into detail using the same server definition.

Next Actions are prioritized by severity, stage, and deadline. Each names the count, impact, suggested action, and direct button—for example, “38 parcels remain unassigned; open map planning.”

## Map Workspace

Map, metrics, and list share one query/filter state. The header states “72 queried / 69 locatable / 3 missing coordinates / 69 displayed.” Initial load fits all points and provides Fit All and Clear Filters.

Stable colours identify unassigned, assigned, data exception, and operational exception; dense points cluster. Metric, legend, and action selection filters both map and list. Empty maps distinguish no orders, filter-empty, all coordinates missing, Google Maps failure, and API failure, with recovery actions.

Only coordinates and styles go to Google Maps. Recipient data loads from OpenDelivery into the detail drawer.

## Test Order Experience Mode

Until upstream APIs are available, `scripts/db/004_r02_experience_seed.sql` simulates canonical ingestion. All keys use `DEMO-R02-*`. Each YHZ/YYZ/YVR station receives six drivers, four published areas, and 72 current-day orders.

Fixtures cover standard/express, upstream/station custody, four areas, missing/out-of-area geocodes, and varied driver capacity. They use production Waybill, Parcel, Geocode, Area Assignment, and planning gates; test actions remain audited.

## Access, Languages, and Acceptance

Operators are station-scoped. Admin station changes cancel old requests and clear old cached state. Navigation, metrics, states, exceptions, and actions support `en-CA`, `fr-CA`, and `zh-CN`.

- A new operator identifies the stage, top blocker, and next action within 60 seconds.
- Every Today number drills down and aggregate/detail counts agree.
- Locatable test orders produce visible initial-map points matching the list count.
- Switching among three stations leaves no previous-station metric, marker, row, or action.
- The core journey can be completed in navigation order without a trainer explaining menus.
- Automation and operator browser acceptance are both required for completion.

