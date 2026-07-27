# M04 Delivery Summary: Mobile Dispatch Map-First Layout

Status: COMPLETED

## Delivered

- Added mobile dispatch layout classes; steps 3/4 hide the fixed left panel and give the map a full-width, near full-screen viewport.
- Added a top map driver selector with a bottom drawer for selecting/clearing a driver and viewing capacity.
- Adapted the parcel floating table for narrow screens while retaining the bottom “open parcel list” action.
- Desktop layout and API contracts are unchanged.

## Verification

- `pnpm run typecheck`: passed.
- `pnpm run build`: passed.
