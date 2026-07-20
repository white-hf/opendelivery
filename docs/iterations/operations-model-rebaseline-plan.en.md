# Operations Model Rebaseline Iteration Plan

The operations review established pre-arrival order/area/capacity planning followed by driver pickup scans, replacing item receipt as the dispatch prerequisite. I02-I06 foundations remain but their DoD is revalidated; the former I07-I15 order is paused.

| Iteration | Outcome | Scope |
|---|---|---|
| R01 | Versioned GeoJSON areas | spatial schema, validation, preferences, audit |
| R01.1 | Shared localization foundation | locale persistence, localized APIs, Web/Android resources, fallback gate |
| R02 | Pre-arrival map planning | geocode, clusters, drawer, shifts, assignment, freeze/release |
| R03 | Physical arrival | trips, handling units, aggregation observations |
| R04 | Own-driver scans | expected lists, rejected scans, damage, submit, idempotency |
| R05 | Reconciliation/handover | progress, cross-task recompute, custody transaction, reassignment |
| R06 | Delivery return | POD, retry, RETURN approval, return-then-assign, timeline |
| R07 | Closeout/automation | reconciliation, drilldown, sign-off, one-command evidence |
| R08 | Five-day pilot | runbook, training, on-call, reports, defects, gate |

R01 status (2026-07-20): V8 and the area lifecycle, preference, and parcel-match APIs are complete. Operations Web supports GeoJSON import, map preview, click drawing, validation, and publication. Web preference editing, shared-edge automation, full three-station E2E, and browser visual acceptance remain; R01 is not complete. Tiles are configured with `VITE_MAP_TILE_URL`/`VITE_MAP_ATTRIBUTION`; default OpenStreetMap tiles are development-only and no parcel/customer data is sent by the area editor.

Publication uses `ST_Intersects` for boundary-inclusive point matching and `ST_Intersects AND NOT ST_Touches` to reject same-level area overlap while allowing a shared edge. Planning records reference immutable published versions so later edits cannot rewrite history.
