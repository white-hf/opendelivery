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

R01 status (2026-07-20): V8 and the area lifecycle, preference, and parcel-match APIs are complete. Operations Web supports GeoJSON import, map preview/click drawing, validation/publication, and default driver priority editing. `scripts/delivery-area-e2e.sh` now verifies on real MySQL that YHZ, YYZ, and YVR can independently create and publish areas, shared edges are accepted, area overlap and cross-station resource access are rejected, and fixtures are removed. Browser visual acceptance remains pending in an environment with browser tooling, so R01 is not yet marked complete.

The Operations Web target is a map-first last-mile control console: map is the primary workspace for areas, parcel distribution, driver tasks, and exceptions, with lists/details in side panels or drawers. R01 uses Google Maps JavaScript API with `VITE_GOOGLE_MAPS_API_KEY`; the key is never committed and production must restrict HTTP referrers/API scope and enable quota/billing alerts. The area editor loads map context only and does not send parcel or recipient business data to Google. The deprecated Drawing library found in the reference tool is not adopted; the application owns click-based boundary capture.

R01 acceptance-defect slice: an administrator's station selector must show every active station; import must accept the common geojson.io `FeatureCollection`, `Feature`, `Polygon`, and `MultiPolygon` forms, allow a local `.geojson/.json` file, and preview it immediately. Click drawing must continuously show vertices, a closed boundary, fill, and point count. Parse errors must be reported before submission. R01 closes after automated regression and operator browser re-acceptance.

R01 experience-acceptance slice: the area editor must fit the viewport, place map and import content side by side on wide screens and stack them on narrow screens, and keep Cancel/Create actions in a persistent modal footer. Business success/failure uses prominent global notifications while field validation stays inline. On a typical laptop viewport, map, import entry, and Create Draft must be visible without scrolling the whole dialog.

Publication uses `ST_Intersects` for boundary-inclusive point matching and `ST_Intersects AND NOT ST_Touches` to reject same-level area overlap while allowing a shared edge. Planning records reference immutable published versions so later edits cannot rewrite history.
