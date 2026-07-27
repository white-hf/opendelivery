# R09 Arrival Map Aggregation and Handling-Unit Distribution Summary

> Status: `COMPLETED` (2026-07-27); plan: [R09 Arrival Map Distribution Fix](../iterations/iteration-r09-arrival-map-distribution.en.md).

The Arrival workspace now owns map area expansion state and reuses the Order Readiness behavior. Whole-trip mode aggregates all linked parcels; selecting a handling unit scopes the counts to that unit. Area cluster badges and polygons expand to parcel points, and switching trips or units clears stale selections. Parcels without coordinates are excluded from map rendering.

The Operations API was rebuilt and restarted. `/arrival-trips/42` now returns 400 parcels with `longitude`, `latitude`, `area_code`, and `area_id`. The root cause was a stale running JAR that did not contain the already-defined coordinate/area projection fields; database relationships and data were complete.

Arrival detail now also projects `driver_id` and `stop_sequence`, allowing already-routed parcels to use the same numbered water-drop marker as Order Readiness.

TypeScript and the production Vite build pass, and all six arrival-coverage unit tests pass. Full Vitest/lint remains blocked by pre-existing baseline failures unrelated to this patch (`dispatch-reassign` navigation, locale key parity, API station-header assertion, and existing global lint errors). No backend API or Flyway schema changed.
