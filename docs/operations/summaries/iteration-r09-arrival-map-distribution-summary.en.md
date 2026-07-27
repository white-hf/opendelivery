# R09 Arrival Map Aggregation and Handling-Unit Distribution Summary

> Status: `COMPLETED` (2026-07-27); plan: [R09 Arrival Map Distribution Fix](../iterations/iteration-r09-arrival-map-distribution.en.md).

The Arrival workspace now owns map area expansion state and reuses the Order Readiness behavior. Whole-trip mode aggregates all linked parcels; selecting a handling unit scopes the counts to that unit. Area cluster badges and polygons expand to parcel points, and switching trips or units clears stale selections. Parcels without coordinates are excluded from map rendering.

TypeScript and the production Vite build pass, and all six arrival-coverage unit tests pass. Full Vitest/lint remains blocked by pre-existing baseline failures unrelated to this patch (`dispatch-reassign` navigation, locale key parity, API station-header assertion, and existing global lint errors). No backend API or Flyway schema changed.
