# M02 Mobile Core Operations

Status: `COMPLETED` (2026-07-27)

## Goal

Allow operators to use a phone browser for order readiness, arrival-batch review, dispatch-plan confirmation, and shipment detail without desktop-only two-column layouts or page-level horizontal scrolling.

## Slice

- Use a single-column order-readiness flow on small screens, with filters followed by the map/list content.
- Stack the arrival trip/unit controls and map vertically while preserving selection and parcel distribution.
- Keep the dispatch SOP semantics; make the step bar horizontally reviewable and the map height viewport-friendly.
- Preserve the existing APIs, station isolation, permissions, and Google Maps behavior.

## Acceptance

- No page-level horizontal overflow from 320px through 768px; long tables scroll only inside their own container.
- Order readiness, arrival, and dispatch planning load, filter, select, and open details on a phone.
- Desktop layouts, Google Maps, and API contracts remain unchanged.
- `pnpm run typecheck` and `pnpm run build` pass.
