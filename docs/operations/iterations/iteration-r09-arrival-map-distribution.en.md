# R09 Arrival Map Aggregation and Handling-Unit Parcel Distribution Fix

> Status: `REVIEWED` (2026-07-27); type: Operations Web bug fix; no Driver API, arrival state-machine, or database change.

## Problem and scope

Order Readiness already shows delivery-area polygons, numeric cluster counts, and expandable parcel points. Arrival reused `PlanningMap` without maintaining area expansion state, so handling-unit selection did not provide a usable geographic distribution view.

The fix keeps the shared map behavior, scopes counts to the selected handling unit (or the whole trip), supports area/cluster expansion, clears stale selections when the trip or unit changes, and keeps parcel detail clicks connected to the existing drawer. It does not add operator-side driver scanning.

## Definition of done

Cover unit filtering, area selection reset, and locatable parcel mapping; pass Web typecheck, Vitest, lint, build, and browser verification for whole-trip clusters, single-unit clusters, expansion, parcel details, and all supported locales. Backend APIs and schema remain unchanged.
