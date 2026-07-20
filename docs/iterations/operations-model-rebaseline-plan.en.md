# Operations Model Rebaseline Iteration Plan

The operations review established pre-arrival order/area/capacity planning followed by driver pickup scans, replacing item receipt as the dispatch prerequisite. I02-I06 foundations remain but their DoD is revalidated; the former I07-I15 order is paused.

| Iteration | Outcome | Scope |
|---|---|---|
| R01 | Versioned GeoJSON areas | spatial schema, validation, preferences, audit |
| R02 | Pre-arrival map planning | geocode, clusters, drawer, shifts, assignment, freeze/release |
| R03 | Physical arrival | trips, handling units, aggregation observations |
| R04 | Own-driver scans | expected lists, rejected scans, damage, submit, idempotency |
| R05 | Reconciliation/handover | progress, cross-task recompute, custody transaction, reassignment |
| R06 | Delivery return | POD, retry, RETURN approval, return-then-assign, timeline |
| R07 | Closeout/automation | reconciliation, drilldown, sign-off, one-command evidence |
| R08 | Five-day pilot | runbook, training, on-call, reports, defects, gate |

R01 status (2026-07-20): Flyway V8 tables and draft/validate/publish/list/version, driver-preference and parcel spatial-match APIs are complete; Operations Web provides GeoJSON import, validation and publication. Real MySQL proves SRID 4326, validity/indexing, point intersection, overlap rejection, persisted matching and cross-station rejection. Embedded map preview/drawing, Web preference editing, shared-edge and full three-station E2E remain; R01 is not complete.

Publication uses `ST_Intersects` for boundary-inclusive point matching and `ST_Intersects AND NOT ST_Touches` to reject same-level area overlap while allowing a shared edge. Planning records reference immutable published versions so later edits cannot rewrite history.
