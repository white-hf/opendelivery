# M03 Mobile Map and Exception Actions

Status: `COMPLETED` (2026-07-27)

## Scope delivered

- Added a fixed bottom high-frequency action bar to order readiness, arrival, and dispatch planning so phone users can open parcel work lists/details.
- Map controls use a vertical small-screen layout so fixed side panels do not squeeze the map; lists remain available through drawers or internal scrolling.
- The bottom action bar is mobile-only; desktop floating controls and layout remain unchanged.
- Existing map selection, area filtering, parcel selection, reassignment, and exception APIs are unchanged.

## Acceptance

- A phone user can open the parcel list or arrival detail from a map page without hitting a tiny floating control.
- The bottom bar does not cover primary content; mobile content reserves bottom safe space.
- `pnpm run typecheck` and `pnpm run build` pass.
