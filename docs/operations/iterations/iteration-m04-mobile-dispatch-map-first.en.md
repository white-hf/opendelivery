# M04: Mobile Dispatch Map-First Layout

Status: COMPLETED

## Goal

Make the map the primary work surface on phones when operators assign drivers and inspect parcels.

## Scope

- Hide the fixed desktop driver panel on mobile during dispatch steps and give the map the main viewport.
- Provide a top map control that opens a bottom drawer with available drivers, capacity, and the current selection.
- Keep parcel details in an in-map floating panel, sized for narrow screens, with a bottom action bar to open it.
- Preserve the existing split-pane, capacity drawer, and parcel table behavior on desktop.

## Acceptance Criteria

- On a 375px viewport, step 3 exposes at least 520px of map height.
- Driver selection, clearing the driver filter, and opening parcel details require no horizontal scrolling.
- Driver/area filters produce the same result in the map and parcel list.
- `pnpm run typecheck` and `pnpm run build` pass.
