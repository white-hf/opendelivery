# M03 Mobile Map and Exception Actions — Delivery Summary

Status: `COMPLETED` (2026-07-27)

## Delivered

- Added the shared `MobileActionBar` to order readiness, arrival, and dispatch planning.
- The mobile bottom bar opens the order queue, arrival parcel detail, and dispatch parcel list, showing the current count.
- Order readiness sidebar, arrival map/unit area, and dispatch map/list rearrange at phone widths while preserving map selection and drawer detail.
- Desktop does not render the bottom bar and keeps its existing floating controls.

## Verification

- Operations Web `pnpm run typecheck` passed.
- Operations Web `pnpm run build` passed.
- The build emitted only the existing large-chunk warning.
